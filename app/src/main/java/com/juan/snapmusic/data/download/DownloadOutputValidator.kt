package com.juan.snapmusic.data.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.juan.snapmusic.core.model.ContainerFormat

class DownloadOutputValidator(
    private val context: Context,
) {
    fun validate(uri: Uri, container: ContainerFormat) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                error("El archivo final quedó inválido después de procesarlo.")
            }
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            when (container) {
                ContainerFormat.MP3 -> if (!mimeType.contains("audio", ignoreCase = true)) {
                    error("El MP3 final no se pudo reproducir correctamente.")
                }

                ContainerFormat.M4A -> if (!mimeType.contains("audio", ignoreCase = true)) {
                    error("El M4A final no se pudo reproducir correctamente.")
                }

                ContainerFormat.MP4 -> {
                    val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    if (hasVideo != "yes") error("El MP4 final no contiene video reproducible.")
                }
            }
        } finally {
            retriever.release()
        }
    }
}
