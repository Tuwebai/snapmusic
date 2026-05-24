package com.juan.snapmusic.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapMusicBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

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
    fun generateBaselineProfile() = baselineProfileRule.collect(
        packageName = packageName,
        includeInStartupProfile = true,
    ) {
        launchHomeAndWait()

        device.findObject(By.text("YouTube"))?.click()
        device.waitForIdle()
        swipeUpOnContent(repetitions = 4)

        device.findObject(By.clazz("android.widget.ImageView"))?.click()
        device.waitForIdle()

        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.42f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.78f).toInt(),
            16,
        )
        device.waitForIdle()

        device.findObject(By.text("Reproducir"))?.click()
        device.waitForIdle()
        swipeUpOnContent(repetitions = 3)
        device.findObject(By.clazz("android.widget.ImageView"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        device.findObject(By.text("Buscar"))?.click()
        device.waitForIdle()
        device.findObject(By.textContains("Buscar para descargar"))?.click()
        device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
        device.findObject(By.clazz("android.widget.EditText"))?.setText("cumbia 2025")
        device.pressEnter()
        device.wait(Until.hasObject(By.clazz("android.widget.ImageView")), 5_000)
    }
}
