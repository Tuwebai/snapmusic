package com.juan.snapmusic

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnapMusicApplication : Application(), Configuration.Provider {
    lateinit var appGraph: SnapMusicGraph
        private set
    private val workExecutor by lazy { Executors.newFixedThreadPool(4) }

    override fun onCreate() {
        super.onCreate()
        appGraph = SnapMusicGraph(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(8)
            .build()

}
