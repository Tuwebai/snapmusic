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
        outputDir.deleteRecursively()
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

        fun notificationSmallIcon(image: BufferedImage, size: Int): BufferedImage {
            val scaled = scale(image, size)
            val masked = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = scaled.getRGB(x, y)
                    val alpha = pixel ushr 24 and 0xff
                    val red = pixel ushr 16 and 0xff
                    val green = pixel ushr 8 and 0xff
                    val blue = pixel and 0xff
                    val isSnapMusicMark = alpha > 20 && red > 96 && red > green + 32 && red > blue + 32
                    val outputAlpha = if (isSnapMusicMark) alpha else 0
                    masked.setRGB(x, y, outputAlpha shl 24 or 0x00ffffff)
                }
            }
            return masked
        }

        fun writePng(image: BufferedImage, path: String) {
            javax.imageio.ImageIO.write(image, "png", output(path))
        }

        writePng(source, "drawable-nodpi/snapmusic_brand_logo.png")
        writePng(source, "drawable-nodpi/snapmusic_brand_badge.png")
        writePng(notificationSmallIcon(source, 96), "drawable-nodpi/ic_stat_snapmusic_real.png")
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
