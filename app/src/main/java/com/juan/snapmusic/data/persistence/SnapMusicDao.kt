package com.juan.snapmusic.data.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapMusicDao {
    @Query("SELECT * FROM queue_entries ORDER BY createdAt DESC")
    fun observeQueue(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestHistory(): Flow<HistoryEntity?>

    @Query("SELECT * FROM youtube_watch_history ORDER BY watchedAt DESC")
    fun observeYouTubeWatchHistory(): Flow<List<YouTubeWatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQueue(entity: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertYouTubeWatchHistory(entity: YouTubeWatchHistoryEntity)

    @Query("SELECT * FROM queue_entries WHERE id = :id LIMIT 1")
    suspend fun getQueueById(id: String): QueueEntity?

    @Query(
        """
        SELECT * FROM queue_entries
        WHERE sourceUrl = :sourceUrl
          AND container = :container
          AND destinationLabel = :destinationLabel
          AND ((destinationTreeUri IS NULL AND :destinationTreeUri IS NULL) OR destinationTreeUri = :destinationTreeUri)
        ORDER BY createdAt DESC
        """,
    )
    suspend fun findQueueCandidates(
        sourceUrl: String,
        container: com.juan.snapmusic.core.model.ContainerFormat,
        destinationLabel: String,
        destinationTreeUri: String?,
    ): List<QueueEntity>

    @Query(
        """
        SELECT laneIndex, COUNT(*) AS total
        FROM queue_entries
        WHERE status IN ('PENDING', 'RUNNING')
        GROUP BY laneIndex
        """,
    )
    suspend fun activeLaneLoads(): List<LaneLoad>

    @Query("UPDATE queue_entries SET status = :pendingStatus WHERE status = :runningStatus")
    suspend fun requeueInterrupted(runningStatus: com.juan.snapmusic.core.model.QueueStatus, pendingStatus: com.juan.snapmusic.core.model.QueueStatus)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM queue_entries WHERE id = :id")
    suspend fun deleteQueue(id: String)
}
