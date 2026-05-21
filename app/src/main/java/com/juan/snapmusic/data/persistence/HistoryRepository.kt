package com.juan.snapmusic.data.persistence

import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(
    private val dao: SnapMusicDao,
) {
    fun observeHistory(): Flow<List<HistoryEntry>> = dao.observeHistory().map { list -> list.map { it.toModel() } }

    fun observeLatest(): Flow<HistoryEntry?> = dao.observeLatestHistory().map { it?.toModel() }

    suspend fun append(
        id: String,
        title: String,
        author: String,
        sourceUrl: String,
        thumbnailUrl: String,
        outputUri: String,
        format: ContainerFormat,
        qualityLabel: String,
    ) {
        dao.upsertHistory(
            HistoryEntity(
                id = id,
                title = title,
                author = author,
                sourceUrl = sourceUrl,
                thumbnailUrl = thumbnailUrl,
                outputUri = outputUri,
                format = format,
                qualityLabel = qualityLabel,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(id: String) {
        dao.deleteHistory(id)
    }
}
