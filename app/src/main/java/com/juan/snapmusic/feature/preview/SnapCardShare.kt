package com.juan.snapmusic.feature.preview

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.drawToBitmap
import com.juan.snapmusic.core.model.LocalMediaItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun shareSnapCard(context: Context, item: LocalMediaItem) {
    val appContext = context.applicationContext
    val artwork = withContext(Dispatchers.IO) {
        loadSnapCardArtwork(appContext, item.thumbnailUrl, item.contentUri)
    }
    val bitmap = withContext(Dispatchers.Main) {
        SnapCardRenderView(
            context = context,
            data = SnapCardRenderData(
                title = item.title.ifBlank { "Nombre de la Canción" },
                artist = item.snapCardArtistName(),
                artwork = artwork,
            ),
        ).toSnapCardBitmap()
    }
    val uri = withContext(Dispatchers.IO) {
        saveSnapCardBitmap(appContext, bitmap)
    }
    bitmap.recycle()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "SnapCard")
        clipData = ClipData.newUri(context.contentResolver, "SnapCard", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        launchChooserOrToast(
            context = context,
            intent = intent,
            title = "Compartir SnapCard",
            errorMessage = "No hay una app disponible para compartir la SnapCard.",
        )
    }
    artwork?.recycle()
}

private fun SnapCardRenderView.toSnapCardBitmap(): Bitmap {
    val exactSize = View.MeasureSpec.makeMeasureSpec(SnapCardRenderView.CardSizePx, View.MeasureSpec.EXACTLY)
    measure(exactSize, exactSize)
    layout(0, 0, SnapCardRenderView.CardSizePx, SnapCardRenderView.CardSizePx)
    return drawToBitmap(Bitmap.Config.ARGB_8888)
}

private fun saveSnapCardBitmap(context: Context, bitmap: Bitmap): Uri {
    val directory = File(context.externalCacheDir ?: context.cacheDir, "snapcards").apply { mkdirs() }
    val file = File(directory, "snapcard-${UUID.randomUUID()}.png")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun loadSnapCardArtwork(context: Context, artworkSource: String, mediaSource: String): Bitmap? {
    return decodeBitmap(context, artworkSource)
        ?: decodeBitmap(context, mediaSource)
        ?: runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaSource.toUri())
                retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } finally {
                retriever.release()
            }
        }.getOrNull()
}

private fun decodeBitmap(context: Context, source: String): Bitmap? {
    if (source.isBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(source.toUri())?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}

private fun LocalMediaItem.snapCardArtistName(): String {
    val fromSubtitle = subtitle
        .substringBefore(" · ")
        .replace("Video local", "")
        .trim()
    return fromSubtitle.ifBlank { "Nombre del Artista" }
}
