package com.juan.snapmusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class IncomingShareItem(
    val url: String,
)

enum class IncomingShareSourceAction {
    SEND,
    SEND_MULTIPLE,
    VIEW,
}

@Immutable
data class IncomingSharePayload(
    val sourceAction: IncomingShareSourceAction,
    val items: List<IncomingShareItem> = emptyList(),
)
