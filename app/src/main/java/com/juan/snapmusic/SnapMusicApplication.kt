package com.juan.snapmusic

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

class SnapMusicApplication : Application(), Configuration.Provider {
    lateinit var appGraph: SnapMusicGraph
        private set
    private val workExecutor by lazy { Executors.newFixedThreadPool(4) }

    override fun onCreate() {
        super.onCreate()
        appGraph = SnapMusicGraph(this)
        cleanupFfmpegWorkDir()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(8)
            .build()

    private fun cleanupFfmpegWorkDir() {
        val ffmpegDir = java.io.File(cacheDir, "ffmpeg")
        val httpTransferDir = java.io.File(cacheDir, "http-transfer")
        Thread {
            try {
                ffmpegDir.listFiles().orEmpty().forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
                httpTransferDir.listFiles().orEmpty().forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true }.start()
    }
}
