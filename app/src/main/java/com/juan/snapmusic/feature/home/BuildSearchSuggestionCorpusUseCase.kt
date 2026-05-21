package com.juan.snapmusic.feature.home

import com.juan.snapmusic.core.model.YouTubeFeedItem

class BuildSearchSuggestionCorpusUseCase {
    operator fun invoke(
        popularQueries: List<String>,
        items: List<YouTubeFeedItem>,
    ): List<String> = buildList {
        addAll(DEFAULT_PRESETS)
        addAll(popularQueries)
        items.forEach { item ->
            add(item.title)
            item.author?.let(::add)
        }
    }
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .take(64)
        .toList()

    private companion object {
        val DEFAULT_PRESETS = listOf("Cumbia 2025", "Cuarteto en vivo", "Mix DJ", "Enganchados", "Roze Oficial")
    }
}
