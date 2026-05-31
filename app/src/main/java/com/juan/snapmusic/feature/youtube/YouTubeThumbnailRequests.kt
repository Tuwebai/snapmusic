package com.juan.snapmusic.feature.youtube

import android.content.Context
import coil.request.ImageRequest
import coil.size.Precision

private const val YOUTUBE_THUMBNAIL_WIDTH_PX = 154
private const val YOUTUBE_THUMBNAIL_HEIGHT_PX = 88

internal fun buildYouTubeThumbnailRequest(
    context: Context,
    thumbnailUrl: String,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .memoryCacheKey(thumbnailUrl)
        .diskCacheKey(thumbnailUrl)
        .crossfade(false)
        .precision(Precision.INEXACT)
        .size(YOUTUBE_THUMBNAIL_WIDTH_PX, YOUTUBE_THUMBNAIL_HEIGHT_PX)
        .build()
}
