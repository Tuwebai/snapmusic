package com.juan.snapmusic.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SnapMusicMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = "com.juan.snapmusic"

    private fun MacrobenchmarkScope.launchHomeAndWait() {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
    }

    private fun MacrobenchmarkScope.swipeUpOnContent(repetitions: Int) {
        val centerX = device.displayWidth / 2
        val startY = (device.displayHeight * 0.78f).toInt()
        val endY = (device.displayHeight * 0.28f).toInt()
        repeat(repetitions) {
            device.swipe(centerX, startY, centerX, endY, 20)
        }
    }

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
    }

    @Test
    fun homeFeedTabsAndPlayback() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            launchHomeAndWait()
        },
    ) {
        device.findObject(By.text("Buscar"))?.click()
        device.waitForIdle()
        val youtubeTab = device.findObject(By.text("YouTube"))
        youtubeTab?.click()
        device.waitForIdle()
        device.findObject(By.text("Convertir"))?.click()
        device.waitForIdle()
        youtubeTab?.click()
        device.waitForIdle()
        swipeUpOnContent(repetitions = 3)

        val firstCard = device.findObject(By.clazz("android.widget.ImageView"))
        firstCard?.click()
        device.waitForIdle()

        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.42f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.78f).toInt(),
            16,
        )
        device.waitForIdle()
        device.findObject(By.text("YouTube"))?.click()
        device.waitForIdle()
    }

    @Test
    fun searchToYoutubeResults() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            launchHomeAndWait()
        },
    ) {
        device.findObject(By.textContains("Buscar para descargar"))?.click()
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
        device.findObject(By.clazz("android.widget.EditText"))?.setText("cumbia 2025")
        device.pressEnter()
        device.wait(Until.hasObject(By.clazz("android.widget.ImageView")), 5_000)
    }

    @Test
    fun youtubeWatchAndMiniplayer() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            launchHomeAndWait()
        },
    ) {
        device.findObject(By.text("YouTube"))?.click()
        device.waitForIdle()
        device.findObject(By.clazz("android.widget.ImageView"))?.click()
        device.waitForIdle()
        val display = device.displayWidth to device.displayHeight
        device.swipe(
            display.first / 2,
            (display.second * 0.42f).toInt(),
            display.first / 2,
            (display.second * 0.78f).toInt(),
            16,
        )
        device.waitForIdle()
    }

    @Test
    fun previewLibraryAndPlayback() = benchmarkRule.measureRepeated(
        packageName = packageName,
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            launchHomeAndWait()
        },
    ) {
        device.findObject(By.text("Reproducir"))?.click()
        device.waitForIdle()

        swipeUpOnContent(repetitions = 3)
        device.findObject(By.clazz("android.widget.ImageView"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
