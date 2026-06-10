package com.juan.snapmusic

import android.content.Context
import androidx.room.Room
import com.juan.snapmusic.data.cleanup.CacheCleanupRepository
import com.juan.snapmusic.data.download.DownloadCoordinator
import com.juan.snapmusic.data.download.DownloadNetworkPolicy
import com.juan.snapmusic.data.download.DownloadOutputValidator
import com.juan.snapmusic.data.download.HttpTransferEngine
import com.juan.snapmusic.data.extractor.CompositeStreamResolverRepository
import com.juan.snapmusic.data.extractor.InstagramStreamResolverRepository
import com.juan.snapmusic.data.extractor.NewPipeStreamResolverRepository
import com.juan.snapmusic.data.extractor.OkHttpNewPipeDownloader
import com.juan.snapmusic.data.persistence.HistoryRepository
import com.juan.snapmusic.data.persistence.QueueRepository
import com.juan.snapmusic.data.persistence.SnapMusicDatabase
import com.juan.snapmusic.data.persistence.YouTubeWatchHistoryRepository
import com.juan.snapmusic.data.recommendation.MusicHomeFeedRepository
import com.juan.snapmusic.data.recommendation.MusicRecommendationEngine
import com.juan.snapmusic.data.storage.LaunchPreferencesRepository
import com.juan.snapmusic.data.storage.PreferencesRepository
import com.juan.snapmusic.data.storage.StorageRepository
import com.juan.snapmusic.data.transcode.FfmpegKitTranscodeEngine
import kotlinx.coroutines.flow.first
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SnapMusicGraph(
    context: Context,
) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val extractorOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val database: SnapMusicDatabase by lazy {
        Room.databaseBuilder(appContext, SnapMusicDatabase::class.java, "snapmusic.db")
            .addMigrations(SnapMusicDatabase.MIGRATION_2_3)
            .addMigrations(SnapMusicDatabase.MIGRATION_3_4)
            .addMigrations(SnapMusicDatabase.MIGRATION_4_5)
            .addMigrations(SnapMusicDatabase.MIGRATION_5_6)
            .addMigrations(SnapMusicDatabase.MIGRATION_6_7)
            .addMigrations(SnapMusicDatabase.MIGRATION_7_8)
            .build()
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(appContext)
    }

    val launchPreferencesRepository: LaunchPreferencesRepository by lazy {
        LaunchPreferencesRepository(appContext)
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

    val youtubeWatchHistoryRepository: YouTubeWatchHistoryRepository by lazy {
        YouTubeWatchHistoryRepository(database.dao())
    }

    private val youtubeResolverRepository by lazy {
        NewPipeStreamResolverRepository(OkHttpNewPipeDownloader(extractorOkHttpClient))
    }

    private val instagramResolverRepository by lazy {
        InstagramStreamResolverRepository(extractorOkHttpClient)
    }

    val resolverRepository by lazy {
        CompositeStreamResolverRepository(
            youtube = youtubeResolverRepository,
            instagram = instagramResolverRepository,
        )
    }

    val musicRecommendationEngine by lazy {
        MusicRecommendationEngine()
    }

    val musicHomeFeedRepository by lazy {
        MusicHomeFeedRepository(
            resolverRepository = resolverRepository,
            preferencesRepository = preferencesRepository,
            historyRepository = historyRepository,
            youtubeWatchHistoryRepository = youtubeWatchHistoryRepository,
            engine = musicRecommendationEngine,
        )
    }

    val transcodeEngine by lazy {
        FfmpegKitTranscodeEngine(appContext)
    }

    val downloadNetworkPolicy by lazy {
        DownloadNetworkPolicy(appContext)
    }

    val httpTransferEngine by lazy {
        HttpTransferEngine(appContext, okHttpClient)
    }

    val downloadOutputValidator by lazy {
        DownloadOutputValidator(appContext)
    }

    val cacheCleanupRepository by lazy {
        CacheCleanupRepository(appContext)
    }

    val downloadCoordinator by lazy {
        DownloadCoordinator(appContext, queueRepository, preferencesRepository)
    }

    suspend fun currentPreferences() = preferencesRepository.preferences.first()
}
