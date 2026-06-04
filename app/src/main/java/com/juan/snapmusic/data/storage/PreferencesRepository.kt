package com.juan.snapmusic.data.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.juan.snapmusic.core.model.AppThemeMode
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadCompleteSound
import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.FavoriteDestination
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.PreviewPlaybackSnapshot
import com.juan.snapmusic.core.model.YouTubePlaybackSnapshot
import com.juan.snapmusic.core.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.snapMusicStore by preferencesDataStore(name = "snapmusic_prefs")

class PreferencesRepository(
    private val context: Context,
) {
    private val audioFormatKey = stringPreferencesKey("audio_format")
    private val audioQualityKey = stringPreferencesKey("audio_quality")
    private val videoQualityKey = stringPreferencesKey("video_quality")
    private val destinationLabelKey = stringPreferencesKey("destination_label")
    private val customTreeUriKey = stringPreferencesKey("custom_tree_uri")
    private val favoriteDestinationsKey = stringPreferencesKey("favorite_destinations")
    private val downloadTasksWifiKey = intPreferencesKey("download_tasks_wifi")
    private val downloadTasksMobileKey = intPreferencesKey("download_tasks_mobile")
    private val downloadSpeedLimitKey = stringPreferencesKey("download_speed_limit")
    private val allowMobileDataDownloadsKey = booleanPreferencesKey("allow_mobile_data_downloads")
    private val downloadCompleteSoundKey = stringPreferencesKey("download_complete_sound")
    private val notifyDownloadProgressKey = booleanPreferencesKey("notify_download_progress")
    private val notifyDownloadCompletedKey = booleanPreferencesKey("notify_download_completed")
    private val notifyRecommendedContentKey = booleanPreferencesKey("notify_recommended_content")
    private val notifyToolUpdatesKey = booleanPreferencesKey("notify_tool_updates")
    private val notifyToolbarAccessKey = booleanPreferencesKey("notify_toolbar_access")
    private val youtubeAutoplayEnabledKey = booleanPreferencesKey("youtube_autoplay_enabled")
    private val youtubePlaybackSnapshotKey = stringPreferencesKey("youtube_playback_snapshot")
    private val previewPlaybackSnapshotKey = stringPreferencesKey("preview_playback_snapshot")
    private val youtubeHomeFeedCacheKey = stringPreferencesKey("youtube_home_feed_cache")
    private val musicAffinitySignalsKey = stringPreferencesKey("music_affinity_signals")
    private val musicFeedImpressionsKey = stringPreferencesKey("music_feed_impressions")
    private val recentSearchQueriesKey = stringPreferencesKey("recent_search_queries")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val previewVolumeKey = floatPreferencesKey("preview_volume")

    val preferences: Flow<UserPreferences> = context.snapMusicStore.data.map { prefs ->
        UserPreferences(
            defaultAudioFormat = prefs[audioFormatKey]?.let(ContainerFormat::valueOf) ?: ContainerFormat.M4A,
            defaultAudioQuality = prefs[audioQualityKey] ?: "320",
            defaultVideoQuality = prefs[videoQualityKey] ?: "720p",
            defaultDestinationLabel = prefs[destinationLabelKey] ?: "Downloads/SnapMusic",
            customTreeUri = prefs[customTreeUriKey],
            favoriteDestinations = decodeFavorites(prefs[favoriteDestinationsKey]),
            downloadTasksWifi = prefs[downloadTasksWifiKey] ?: 4,
            downloadTasksMobile = prefs[downloadTasksMobileKey] ?: 2,
            downloadSpeedLimitLabel = prefs[downloadSpeedLimitKey] ?: "Sin límites",
            allowMobileDataDownloads = prefs[allowMobileDataDownloadsKey] ?: true,
            downloadCompleteSound = DownloadCompleteSound.fromPreferenceKey(prefs[downloadCompleteSoundKey]),
            notifyDownloadProgress = prefs[notifyDownloadProgressKey] ?: true,
            notifyDownloadCompleted = prefs[notifyDownloadCompletedKey] ?: true,
            notifyRecommendedContent = prefs[notifyRecommendedContentKey] ?: true,
            notifyToolUpdates = prefs[notifyToolUpdatesKey] ?: true,
            notifyToolbarAccess = prefs[notifyToolbarAccessKey] ?: true,
            youtubeAutoplayEnabled = prefs[youtubeAutoplayEnabledKey] ?: true,
            themeMode = AppThemeMode.DARK,
            previewVolume = prefs[previewVolumeKey] ?: 0.9f,
        )
    }

    val youTubePlaybackSnapshot: Flow<YouTubePlaybackSnapshot?> = context.snapMusicStore.data.map { prefs ->
        YouTubePlaybackSnapshotCodec.decode(prefs[youtubePlaybackSnapshotKey])
    }

    val previewPlaybackSnapshot: Flow<PreviewPlaybackSnapshot?> = context.snapMusicStore.data.map { prefs ->
        PreviewPlaybackSnapshotCodec.decode(prefs[previewPlaybackSnapshotKey])
    }

    suspend fun setCustomTree(treeUri: String?, label: String) {
        context.snapMusicStore.edit { prefs ->
            prefs[destinationLabelKey] = label
            if (treeUri == null) {
                prefs.remove(customTreeUriKey)
            } else {
                prefs[customTreeUriKey] = treeUri
            }
        }
    }

    suspend fun updateAudioFormat(format: ContainerFormat) {
        context.snapMusicStore.edit { it[audioFormatKey] = format.name }
    }

    suspend fun updateAudioQuality(value: String) {
        context.snapMusicStore.edit { it[audioQualityKey] = value }
    }

    suspend fun updateVideoQuality(value: String) {
        context.snapMusicStore.edit { it[videoQualityKey] = value }
    }

    suspend fun updatePreviewVolume(value: Float) {
        context.snapMusicStore.edit { it[previewVolumeKey] = value }
    }

    suspend fun updateDownloadTaskLimits(wifi: Int, mobile: Int) {
        context.snapMusicStore.edit { prefs ->
            prefs[downloadTasksWifiKey] = wifi
            prefs[downloadTasksMobileKey] = mobile
        }
    }

    suspend fun updateDownloadSpeedLimitLabel(value: String) {
        context.snapMusicStore.edit { it[downloadSpeedLimitKey] = value }
    }

    suspend fun updateAllowMobileDataDownloads(value: Boolean) {
        context.snapMusicStore.edit { it[allowMobileDataDownloadsKey] = value }
    }

    suspend fun updateDownloadCompleteSound(value: DownloadCompleteSound) {
        context.snapMusicStore.edit { it[downloadCompleteSoundKey] = value.preferenceKey }
    }

    suspend fun updateNotifyDownloadProgress(value: Boolean) {
        context.snapMusicStore.edit { it[notifyDownloadProgressKey] = value }
    }

    suspend fun updateNotifyDownloadCompleted(value: Boolean) {
        context.snapMusicStore.edit { it[notifyDownloadCompletedKey] = value }
    }

    suspend fun updateNotifyRecommendedContent(value: Boolean) {
        context.snapMusicStore.edit { it[notifyRecommendedContentKey] = value }
    }

    suspend fun updateNotifyToolUpdates(value: Boolean) {
        context.snapMusicStore.edit { it[notifyToolUpdatesKey] = value }
    }

    suspend fun updateNotifyToolbarAccess(value: Boolean) {
        context.snapMusicStore.edit { it[notifyToolbarAccessKey] = value }
    }

    suspend fun updateYouTubeAutoplayEnabled(value: Boolean) {
        context.snapMusicStore.edit { it[youtubeAutoplayEnabledKey] = value }
    }

    suspend fun updateThemeMode(value: AppThemeMode) {
        context.snapMusicStore.edit { it[themeModeKey] = AppThemeMode.DARK.name }
    }

    suspend fun saveYouTubePlaybackSnapshot(snapshot: YouTubePlaybackSnapshot) {
        val encoded = YouTubePlaybackSnapshotCodec.encode(snapshot)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(youtubePlaybackSnapshotKey)
            } else {
                prefs[youtubePlaybackSnapshotKey] = encoded
            }
        }
    }

    suspend fun readYouTubePlaybackSnapshot(): YouTubePlaybackSnapshot? {
        val prefs = context.snapMusicStore.data.first()
        return YouTubePlaybackSnapshotCodec.decode(prefs[youtubePlaybackSnapshotKey])
    }

    suspend fun clearYouTubePlaybackSnapshot() {
        context.snapMusicStore.edit { it.remove(youtubePlaybackSnapshotKey) }
    }

    suspend fun savePreviewPlaybackSnapshot(snapshot: PreviewPlaybackSnapshot) {
        val encoded = PreviewPlaybackSnapshotCodec.encode(snapshot)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(previewPlaybackSnapshotKey)
            } else {
                prefs[previewPlaybackSnapshotKey] = encoded
            }
        }
    }

    suspend fun readPreviewPlaybackSnapshot(): PreviewPlaybackSnapshot? {
        val prefs = context.snapMusicStore.data.first()
        return PreviewPlaybackSnapshotCodec.decode(prefs[previewPlaybackSnapshotKey])
    }

    suspend fun clearPreviewPlaybackSnapshot() {
        context.snapMusicStore.edit { it.remove(previewPlaybackSnapshotKey) }
    }

    suspend fun saveYouTubeHomeFeedCache(items: List<com.juan.snapmusic.core.model.YouTubeFeedItem>) {
        val encoded = YouTubePlaybackSnapshotCodec.encodeFeed(items)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(youtubeHomeFeedCacheKey)
            } else {
                prefs[youtubeHomeFeedCacheKey] = encoded
            }
        }
    }

    suspend fun readYouTubeHomeFeedCache(): List<com.juan.snapmusic.core.model.YouTubeFeedItem> {
        val prefs = context.snapMusicStore.data.first()
        return YouTubePlaybackSnapshotCodec.decodeFeed(prefs[youtubeHomeFeedCacheKey])
    }

    suspend fun appendMusicAffinitySignal(
        signal: MusicAffinitySignal,
        maxItems: Int = 320,
    ) {
        val current = readMusicAffinitySignals()
        val updated = (current + signal)
            .sortedByDescending(MusicAffinitySignal::timestampMs)
            .take(maxItems)
        val encoded = MusicRecommendationCodec.encodeSignals(updated)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(musicAffinitySignalsKey)
            } else {
                prefs[musicAffinitySignalsKey] = encoded
            }
        }
    }

    suspend fun readMusicAffinitySignals(): List<MusicAffinitySignal> {
        val prefs = context.snapMusicStore.data.first()
        return MusicRecommendationCodec.decodeSignals(prefs[musicAffinitySignalsKey])
    }

    suspend fun rememberRecentSearchQuery(
        query: String,
        maxItems: Int = 20,
    ): List<String> {
        val normalized = query.trim()
        if (normalized.isBlank()) return readRecentSearchQueries()
        val updated = (listOf(normalized) + readRecentSearchQueries())
            .distinctBy { it.lowercase() }
            .take(maxItems)
        val encoded = MusicRecommendationCodec.encodeSearchQueries(updated)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(recentSearchQueriesKey)
            } else {
                prefs[recentSearchQueriesKey] = encoded
            }
        }
        return updated
    }

    suspend fun readRecentSearchQueries(): List<String> {
        val prefs = context.snapMusicStore.data.first()
        return MusicRecommendationCodec.decodeSearchQueries(prefs[recentSearchQueriesKey]).take(20)
    }

    suspend fun saveMusicFeedImpressions(items: List<FeedImpression>) {
        val encoded = MusicRecommendationCodec.encodeImpressions(items)
        context.snapMusicStore.edit { prefs ->
            if (encoded.isBlank()) {
                prefs.remove(musicFeedImpressionsKey)
            } else {
                prefs[musicFeedImpressionsKey] = encoded
            }
        }
    }

    suspend fun readMusicFeedImpressions(): List<FeedImpression> {
        val prefs = context.snapMusicStore.data.first()
        return MusicRecommendationCodec.decodeImpressions(prefs[musicFeedImpressionsKey])
    }

    suspend fun saveFavoriteDestinations(items: List<FavoriteDestination>) {
        context.snapMusicStore.edit { prefs ->
            prefs[favoriteDestinationsKey] = encodeFavorites(items)
        }
    }

    private fun encodeFavorites(items: List<FavoriteDestination>): String {
        return items.joinToString("||") { "${it.label}##${it.treeUri.orEmpty()}" }
    }

    private fun decodeFavorites(raw: String?): List<FavoriteDestination> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("||").mapNotNull { item ->
            val parts = item.split("##")
            val label = parts.firstOrNull()?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            FavoriteDestination(label = label, treeUri = parts.getOrNull(1)?.ifBlank { null })
        }
    }
}
