package com.juan.snapmusic

import android.app.Application

class SnapMusicApplication : Application() {
    lateinit var appGraph: SnapMusicGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = SnapMusicGraph(this)
    }
}
