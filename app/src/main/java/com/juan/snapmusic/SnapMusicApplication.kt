package com.juan.snapmusic

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnapMusicApplication : Application(), Configuration.Provider, ImageLoaderFactory {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .dispatcher(Dispatchers.IO.limitedParallelism(2))
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }

}
