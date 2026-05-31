package com.juan.snapmusic.feature.youtube

import android.content.Context
import coil.request.ImageRequest
import coil.size.Precision

internal fun buildYouTubeThumbnailRequest(
    context: Context,
    thumbnailUrl: String,
    widthPx: Int,
    heightPx: Int,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .memoryCacheKey(thumbnailUrl)
        .diskCacheKey(thumbnailUrl)
        .crossfade(false)
        .precision(Precision.INEXACT)
        .size(widthPx, heightPx)
        .build()
}
