package com.juan.snapmusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class CacheCleanupUiState(
    val isRunning: Boolean = false,
    val feedback: String? = null,
)
