package com.juan.snapmusic.feature.home

import com.juan.snapmusic.core.model.ResolvedMedia

data class HomeUiState(
    val url: String = "",
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
    val resolvedMedia: ResolvedMedia? = null,
    val clipboardCandidateUrl: String? = null,
    val autoOpenFormats: Boolean = false,
)
