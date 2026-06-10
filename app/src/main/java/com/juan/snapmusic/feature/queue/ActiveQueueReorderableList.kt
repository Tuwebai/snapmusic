package com.juan.snapmusic.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.QueueStatus
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ActiveQueueHeaderItems = 1

@Composable
internal fun ActiveQueueReorderableList(
    activeItems: List<QueueEntry>,
    archivedItems: List<QueueEntry>,
    onPause: (QueueEntry) -> Unit,
    onResume: (QueueEntry) -> Unit,
    onCancel: (QueueEntry) -> Unit,
    onRemove: (QueueEntry) -> Unit,
    onReorderPending: (List<String>) -> Unit,
) {
    var visualItems by remember { mutableStateOf(activeItems) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(activeItems) {
        if (!dragging) visualItems = activeItems
    }
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index - ActiveQueueHeaderItems
        val toIndex = to.index - ActiveQueueHeaderItems
        if (fromIndex !in visualItems.indices || toIndex !in visualItems.indices) return@rememberReorderableLazyListState
        if (visualItems[fromIndex].status != QueueStatus.PENDING || visualItems[toIndex].status != QueueStatus.PENDING) {
            return@rememberReorderableLazyListState
        }
        visualItems = visualItems.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun commitPendingOrder() {
        dragging = false
        onReorderPending(visualItems.filter { it.status == QueueStatus.PENDING }.map { it.id })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        if (activeItems.isEmpty()) {
            item {
                if (archivedItems.isEmpty()) {
                    EmptyQueueState()
                } else {
                    EmptyQueueTabState(
                        title = "No hay descargas activas",
                        subtitle = "Pasate a Historial para ver lo último que ya salió de la cola.",
                    )
                }
            }
            return@LazyColumn
        }
        item {
            SectionTitle("Descargas activas", "${activeItems.size} en curso")
        }
        items(
            items = visualItems,
            key = { it.id },
            contentType = { "active_queue_item" },
        ) { item ->
            ReorderableItem(reorderableState, key = item.id) {
                ActiveQueueCard(
                    item = item,
                    onPause = { onPause(item) },
                    onResume = { onResume(item) },
                    onCancel = { onCancel(item) },
                    onRemove = { onRemove(item) },
                    dragHandle = if (item.status == QueueStatus.PENDING) {
                        {
                            QueueReorderHandle(
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        commitPendingOrder()
                                    },
                                ),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueReorderHandle(modifier: Modifier = Modifier) {
    IconButton(
        modifier = modifier.size(36.dp),
        onClick = {},
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "≡",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
    }
}
