package com.juan.snapmusic

import android.content.Context
import androidx.room.Room
import com.juan.snapmusic.data.download.DownloadCoordinator
import com.juan.snapmusic.data.extractor.NewPipeStreamResolverRepository
import com.juan.snapmusic.data.extractor.OkHttpNewPipeDownloader
import com.juan.snapmusic.data.persistence.HistoryRepository
import com.juan.snapmusic.data.persistence.QueueRepository
import com.juan.snapmusic.data.persistence.SnapMusicDatabase
import com.juan.snapmusic.data.recommendation.MusicHomeFeedRepository
import com.juan.snapmusic.data.recommendation.MusicRecommendationEngine
import com.juan.snapmusic.data.storage.PreferencesRepository
import com.juan.snapmusic.data.storage.StorageRepository
import com.juan.snapmusic.data.transcode.FfmpegKitTranscodeEngine
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

class SnapMusicGraph(
    context: Context,
) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val database: SnapMusicDatabase by lazy {
        Room.databaseBuilder(appContext, SnapMusicDatabase::class.java, "snapmusic.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(appContext)
    }

    val storageRepository: StorageRepository by lazy {
        StorageRepository(appContext, preferencesRepository)
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepository(database.dao())
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.dao())
    }

    val resolverRepository by lazy {
        NewPipeStreamResolverRepository(OkHttpNewPipeDownloader(okHttpClient))
    }

    val musicRecommendationEngine by lazy {
        MusicRecommendationEngine()
    }

    val musicHomeFeedRepository by lazy {
        MusicHomeFeedRepository(
            resolverRepository = resolverRepository,
            preferencesRepository = preferencesRepository,
            historyRepository = historyRepository,
            engine = musicRecommendationEngine,
        )
    }

    val transcodeEngine by lazy {
        FfmpegKitTranscodeEngine(appContext)
    }

    val downloadCoordinator by lazy {
        DownloadCoordinator(appContext, queueRepository)
    }

    suspend fun currentPreferences() = preferencesRepository.preferences.first()
}
