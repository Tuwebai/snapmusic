package com.juan.snapmusic.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.juan.snapmusic.core.model.LocalMediaItem
import com.juan.snapmusic.core.model.UserPreferences
import com.juan.snapmusic.core.platform.sanitizeFileName
import java.io.File

class StorageRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) {
    private val localMediaCacheLock = Any()

    @Volatile
    private var cachedLocalMedia: List<LocalMediaItem>? = null

    fun listLocalMedia(forceRefresh: Boolean = false): List<LocalMediaItem> {
        if (!forceRefresh) {
            cachedLocalMedia?.let { return it }
        }
        return synchronized(localMediaCacheLock) {
            if (!forceRefresh) {
                cachedLocalMedia?.let { return@synchronized it }
            }
            runCatching {
                buildList {
                    addAll(queryAudioMedia())
                    addAll(queryVideoMedia())
                }.sortedByDescending { it.dateAdded }
            }.getOrDefault(emptyList()).also { snapshot ->
                cachedLocalMedia = snapshot
            }
        }
    }

    fun invalidateLocalMediaCache() {
        cachedLocalMedia = null
    }

    suspend fun persistPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    suspend fun setCustomTree(uri: Uri?, label: String) {
        preferencesRepository.setCustomTree(uri?.toString(), label)
    }

    fun createDestinationUri(
        preferences: UserPreferences,
        fileName: String,
        mimeType: String,
    ): Uri {
        val customTree = preferences.customTreeUri?.let(Uri::parse)
        if (customTree != null) {
            val documentUri = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, customTree)
                ?: throw IllegalStateException("No se pudo abrir la carpeta elegida.")
            return documentUri.createFile(mimeType, fileName)?.uri
                ?: throw IllegalStateException("No se pudo crear el archivo destino.")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when {
                mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.TITLE, fileName.substringBeforeLast('.', fileName))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SnapMusic")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            context.contentResolver.insert(collection, values)
                ?: error("No se pudo reservar el archivo en Downloads.")
        } else {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, "SnapMusic").apply { mkdirs() }
            Uri.fromFile(File(dir, fileName))
        }
    }

    fun publishOutput(uri: Uri) {
        runCatching {
            when (uri.scheme) {
                "content" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, values, null, null)
                    }
                }

                "file", null -> {
                    uri.path?.let { path ->
                        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                    }
                }
            }
        }
        invalidateLocalMediaCache()
    }

    fun deleteOutput(uriString: String): Boolean {
        val uri = Uri.parse(uriString)
        return runCatching {
            when (uri.scheme) {
                "content" -> {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    deletedRows > 0 || DocumentFile.fromSingleUri(context, uri)?.delete() == true
                }
                "file", null -> {
                    val path = uri.path ?: return@runCatching false
                    File(path).delete()
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    fun renameLocalMedia(
        uriString: String,
        currentFileName: String,
        requestedTitle: String,
    ): Boolean {
        val uri = Uri.parse(uriString)
        val sanitizedTitle = sanitizeFileName(requestedTitle).trim().ifBlank { return false }
        val extension = currentFileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
        val displayName = if (extension != null && !sanitizedTitle.endsWith(".$extension", ignoreCase = true)) {
            "$sanitizedTitle.$extension"
        } else {
            sanitizedTitle
        }
        val title = displayName.substringBeforeLast('.', displayName)
        return runCatching {
            when (uri.scheme) {
                "content" -> {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.TITLE, title)
                    }
                    context.contentResolver.update(uri, values, null, null) > 0
                }
                "file", null -> {
                    val path = uri.path ?: return@runCatching false
                    val source = File(path)
                    val target = File(source.parentFile, displayName)
                    source.exists() && source.renameTo(target)
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun queryAudioMedia(): List<LocalMediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        return buildList {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val duration = cursor.getLong(durationIndex)
                    val size = cursor.getLong(sizeIndex)
                    if (duration <= 0L || size <= 0L) continue
                    val artist = cursor.getString(artistIndex).orEmpty().ifBlank { "Audio local" }
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val albumId = cursor.getLong(albumIdIndex)
                    val artworkUri = if (albumId > 0) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                    } else {
                        ""
                    }

                    add(
                        LocalMediaItem(
                            id = "audio-$id",
                            title = cursor.getString(titleIndex).orEmpty().ifBlank { "Audio sin título" },
                            subtitle = "$artist · ${formatMediaDuration(duration)}",
                            contentUri = contentUri.toString(),
                            fileName = cursor.getString(displayNameIndex).orEmpty(),
                            thumbnailUrl = artworkUri,
                            isVideo = false,
                            durationMs = duration,
                            dateAdded = cursor.getLong(dateIndex),
                        ),
                    )
                }
            }
        }
    }

    private fun queryVideoMedia(): List<LocalMediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
        )

        return buildList {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val duration = cursor.getLong(durationIndex)
                    val size = cursor.getLong(sizeIndex)
                    if (duration <= 0L || size <= 0L) continue
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    add(
                        LocalMediaItem(
                            id = "video-$id",
                            title = cursor.getString(titleIndex).orEmpty().ifBlank { "Video sin título" },
                            subtitle = "Video local · ${formatMediaDuration(duration)}",
                            contentUri = contentUri.toString(),
                            fileName = cursor.getString(displayNameIndex).orEmpty(),
                            thumbnailUrl = contentUri.toString(),
                            isVideo = true,
                            durationMs = duration,
                            dateAdded = cursor.getLong(dateIndex),
                        ),
                    )
                }
            }
        }
    }

    private fun formatMediaDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "--:--"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
