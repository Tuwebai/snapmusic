import java.awt.RenderingHints
import java.awt.image.BufferedImage

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

val snapMusicIconResDir = layout.buildDirectory.dir("generated/res/snapmusicIcons")
val syncSnapMusicIcons by tasks.registering {
    val sourceIcon = rootProject.layout.projectDirectory.file("assets/images/favicon.png")
    inputs.file(sourceIcon)
    outputs.dir(snapMusicIconResDir)

    doLast {
        val outputDir = snapMusicIconResDir.get().asFile
        val source = javax.imageio.ImageIO.read(sourceIcon.asFile)

        fun output(path: String): java.io.File {
            return outputDir.resolve(path).apply { parentFile.mkdirs() }
        }

        fun scale(image: BufferedImage, size: Int): BufferedImage {
            val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val graphics = scaled.createGraphics()
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY,
            )
            graphics.drawImage(image, 0, 0, size, size, null)
            graphics.dispose()
            return scaled
        }

        fun foreground(image: BufferedImage, whiteMask: Boolean): BufferedImage {
            val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    val argb = image.getRGB(x, y)
                    val alpha = argb ushr 24 and 0xff
                    val red = argb ushr 16 and 0xff
                    val green = argb ushr 8 and 0xff
                    val blue = argb and 0xff
                    val isBackground = alpha == 0 || maxOf(red, green, blue) < 18
                    if (!isBackground) {
                        val color = if (whiteMask) 0x00ffffff else (argb and 0x00ffffff)
                        result.setRGB(x, y, (alpha shl 24) or color)
                    }
                }
            }
            return result
        }

        fun writePng(image: BufferedImage, path: String) {
            javax.imageio.ImageIO.write(image, "png", output(path))
        }

        fun writeText(path: String, value: String) {
            output(path).writeText(value.trimIndent(), Charsets.UTF_8)
        }

        val logoForeground = foreground(source, whiteMask = false)
        val notificationMask = foreground(source, whiteMask = true)
        writePng(source, "drawable-nodpi/snapmusic_brand_logo.png")
        writePng(logoForeground, "drawable-nodpi/snapmusic_brand_foreground.png")
        writePng(logoForeground, "drawable-nodpi/snapmusic_brand_badge.png")
        writePng(scale(notificationMask, 96), "drawable-nodpi/ic_stat_snapmusic_real.png")
        mapOf(
            "mipmap-mdpi/ic_launcher.png" to 48,
            "mipmap-hdpi/ic_launcher.png" to 72,
            "mipmap-xhdpi/ic_launcher.png" to 96,
            "mipmap-xxhdpi/ic_launcher.png" to 144,
            "mipmap-xxxhdpi/ic_launcher.png" to 192,
            "mipmap-mdpi/ic_launcher_round.png" to 48,
            "mipmap-hdpi/ic_launcher_round.png" to 72,
            "mipmap-xhdpi/ic_launcher_round.png" to 96,
            "mipmap-xxhdpi/ic_launcher_round.png" to 144,
            "mipmap-xxxhdpi/ic_launcher_round.png" to 192,
        ).forEach { (path, size) -> writePng(scale(source, size), path) }
        writeText(
            "values/snapmusic_icon_colors.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="snapmusic_icon_background">#050505</color>
            </resources>
            """,
        )
        listOf("ic_launcher", "ic_launcher_round").forEach { name ->
            writeText(
                "mipmap-anydpi-v26/$name.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@color/snapmusic_icon_background" />
                    <foreground android:drawable="@drawable/snapmusic_brand_foreground" />
                </adaptive-icon>
                """,
            )
        }
    }
}

android {
    namespace = "com.juan.snapmusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.juan.snapmusic"
        minSdk = 24
        targetSdk = 34
        versionCode = 338
        versionName = "1.0.338"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isProfileable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets["main"].res.srcDir(snapMusicIconResDir)

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    baselineProfile {
        automaticGenerationDuringBuild = false
        saveInSrc = true
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(syncSnapMusicIcons)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.metrics:metrics-performance:1.0.0-alpha04")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.okhttp)
    implementation(libs.google.material)
    implementation(libs.newpipe.extractor)
    implementation(files("libs/ffmpeg-kit-full-6.1.4.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
    baselineProfile(project(":baselineprofile"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
