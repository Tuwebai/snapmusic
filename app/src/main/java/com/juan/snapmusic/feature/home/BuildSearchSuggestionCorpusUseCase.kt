package com.juan.snapmusic.feature.home

import com.juan.snapmusic.core.model.HistoryEntry
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry

class BuildSearchSuggestionCorpusUseCase {
    operator fun invoke(
        popularQueries: List<String>,
        items: List<YouTubeFeedItem>,
        downloadHistory: List<HistoryEntry> = emptyList(),
        watchHistory: List<YouTubeWatchHistoryEntry> = emptyList(),
    ): List<String> = buildList {
        addAll(popularQueries)
        watchHistory.forEach { entry ->
            add(entry.title)
            add(entry.author)
        }
        downloadHistory.forEach { entry ->
            add(entry.title)
            add(entry.author)
        }
        items.forEach { item ->
            add(item.title)
            add(item.author)
        }
    }
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .take(64)
        .toList()
}
