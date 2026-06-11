package com.juan.snapmusic.feature.youtube

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.formatDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class FullscreenTrayAnchor {
    Hidden,
    Expanded,
}

private val FullscreenTrayPeekHeight = 72.dp
private val FullscreenTrayCorner = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.YouTubeFullscreenOverlay(
    watchNextItems: List<YouTubeFeedItem>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onSelectItem: (YouTubeFeedItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(density) {
        AnchoredDraggableState(
            initialValue = FullscreenTrayAnchor.Hidden,
            positionalThreshold = { distance -> distance * 0.35f },
            velocityThreshold = { with(density) { 620.dp.toPx() } },
            snapAnimationSpec = tween<Float>(durationMillis = 190, easing = FastOutSlowInEasing),
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { true },
        )
    }
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val trayHeightPx = constraints.maxHeight * 0.34f
        LaunchedEffect(state, trayHeightPx) {
            state.updateAnchors(
                DraggableAnchors {
                    FullscreenTrayAnchor.Expanded at 0f
                    FullscreenTrayAnchor.Hidden at trayHeightPx
                },
                state.targetValue,
            )
        }

        val rawOffset = state.offset.takeUnless { it.isNaN() } ?: trayHeightPx
        val isOpen = state.currentValue == FullscreenTrayAnchor.Expanded ||
            state.targetValue == FullscreenTrayAnchor.Expanded ||
            rawOffset < trayHeightPx * 0.92f

        if (isOpen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch { state.settle(Float.POSITIVE_INFINITY) }
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(FullscreenTrayPeekHeight)
                .anchoredDraggable(state, Orientation.Vertical),
        )

        FullscreenWatchNextTray(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { trayHeightPx.toDp() })
                .graphicsLayer { translationY = rawOffset }
                .anchoredDraggable(state, Orientation.Vertical),
            items = watchNextItems,
            canLoadMore = canLoadMore,
            isLoadingMore = isLoadingMore,
            onSelectItem = { item ->
                scope.launch { state.settle(Float.POSITIVE_INFINITY) }
                onSelectItem(item)
            },
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun FullscreenWatchNextTray(
    items: List<YouTubeFeedItem>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onSelectItem: (YouTubeFeedItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size, canLoadMore, isLoadingMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = items.isEmpty() || lastVisible >= items.lastIndex - 3
            nearEnd && !listState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && canLoadMore && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    Surface(
        modifier = modifier,
        shape = FullscreenTrayCorner,
        color = Color.Black.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(TextSecondary.copy(alpha = 0.45f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Relacionados", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                if (isLoadingMore) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
                }
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = items,
                    key = { it.url },
                    contentType = { "fullscreen_watch_next_card" },
                ) { item ->
                    FullscreenWatchNextCard(item = item, onClick = { onSelectItem(item) })
                }
            }
        }
    }
}

@Composable
private fun FullscreenWatchNextCard(
    item: YouTubeFeedItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnailRequest = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(336, 188)
            .build()
    }
    Column(
        modifier = Modifier
            .width(188.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            if (item.durationSeconds > 0L) {
                Text(
                    text = formatDuration(item.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
