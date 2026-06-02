package com.juan.snapmusic.data.cleanup

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheCleanupResult(
    val bytesFreed: Long,
)

class CacheCleanupRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cacheDir = appContext.cacheDir

    suspend fun cleanManualCache(): CacheCleanupResult = withContext(Dispatchers.IO) {
        val targets = listOf(
            File(cacheDir, IMAGE_CACHE_DIR),
            File(cacheDir, FFMPEG_WORK_DIR),
            File(cacheDir, HTTP_TRANSFER_DIR),
        )
        val beforeBytes = targets.sumOf(::cacheTreeSizeBytes)
        cleanImageCache()
        targets.drop(1).forEach(::deleteCacheDirectoryContents)
        val afterBytes = targets.sumOf(::cacheTreeSizeBytes)
        CacheCleanupResult(bytesFreed = (beforeBytes - afterBytes).coerceAtLeast(0L))
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun cleanImageCache() {
        runCatching {
            val imageLoader = appContext.imageLoader
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }.onFailure {
            deleteCacheDirectoryContents(File(cacheDir, IMAGE_CACHE_DIR))
        }
    }

    private fun deleteCacheDirectoryContents(directory: File) {
        val root = cacheDir.canonicalFile
        val target = directory.canonicalFile
        if (!target.path.startsWith(root.path)) return
        target.listFiles()?.forEach { child -> child.deleteRecursively() }
        target.mkdirs()
    }

    private fun cacheTreeSizeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::cacheTreeSizeBytes) ?: 0L
    }

    private companion object {
        const val IMAGE_CACHE_DIR = "image_cache"
        const val FFMPEG_WORK_DIR = "ffmpeg"
        const val HTTP_TRANSFER_DIR = "http-transfer"
    }
}
