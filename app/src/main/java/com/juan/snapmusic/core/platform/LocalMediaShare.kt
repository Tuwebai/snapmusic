package com.juan.snapmusic.core.platform

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.juan.snapmusic.core.model.ContainerFormat
import java.io.File

fun buildShareIntent(
    context: Context,
    outputUri: String,
    title: String,
    format: ContainerFormat,
): Intent? {
    val shareUri = context.toLocalContentUri(outputUri) ?: return null

    return Intent(Intent.ACTION_SEND).apply {
        type = format.toMimeType()
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_TITLE, title)
        clipData = ClipData.newUri(context.contentResolver, title, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun buildLocalMediaShareIntent(
    context: Context,
    contentUri: String,
    title: String,
    fileName: String,
    isVideo: Boolean,
): Intent? {
    val shareUri = context.toLocalContentUri(contentUri) ?: return null
    return Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(shareUri)
            ?: fileName.substringAfterLast('.', "").lowercase().let { extension ->
                when {
                    isVideo || extension == "mp4" || extension == "mkv" || extension == "webm" -> "video/*"
                    extension == "m4a" -> "audio/mp4"
                    extension == "mp3" -> "audio/mpeg"
                    else -> "audio/*"
                }
            }
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_TITLE, title)
        clipData = ClipData.newUri(context.contentResolver, title, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun buildOpenFileIntent(
    context: Context,
    outputUri: String,
    title: String,
    format: ContainerFormat,
): Intent? {
    val fileUri = context.toLocalContentUri(outputUri) ?: return null
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fileUri, format.toMimeType())
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun buildOpenFolderIntent(
    context: Context,
    outputUri: String,
): Intent {
    val rawUri = Uri.parse(outputUri)
    val folderUri = when (rawUri.scheme) {
        "content" -> DocumentFile.fromSingleUri(context, rawUri)?.parentFile?.uri
        else -> null
    }

    return if (folderUri != null) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    } else {
        Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
    }
}

private fun Context.toLocalContentUri(outputUri: String): Uri? {
    val rawUri = Uri.parse(outputUri)
    return when (rawUri.scheme) {
        "content" -> rawUri
        "file", null -> {
            val path = rawUri.path ?: return null
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                File(path),
            )
        }
        else -> null
    }
}

private fun ContainerFormat.toMimeType(): String {
    return when (this) {
        ContainerFormat.MP3 -> "audio/mpeg"
        ContainerFormat.M4A -> "audio/mp4"
        ContainerFormat.MP4 -> "video/mp4"
    }
}
