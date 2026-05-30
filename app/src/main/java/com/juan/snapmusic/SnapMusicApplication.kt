package com.juan.snapmusic

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnapMusicApplication : Application(), Configuration.Provider {
    lateinit var appGraph: SnapMusicGraph
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workExecutor by lazy { Executors.newFixedThreadPool(4) }

    override fun onCreate() {
        super.onCreate()
        appGraph = SnapMusicGraph(this)
        appScope.launch {
            if (!appGraph.launchPreferencesRepository.isInitialized()) {
                appGraph.launchPreferencesRepository.syncFromLegacy(appGraph.currentPreferences())
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(workExecutor)
            .setMaxSchedulerLimit(8)
            .build()

}
