package com.juan.snapmusic.data.transcode

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.juan.snapmusic.core.model.ContainerFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FfmpegKitTranscodeEngine(
    context: Context,
) : TranscodeEngine {
    private val workDir = File(context.cacheDir, "ffmpeg").apply { mkdirs() }

    override suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String): Uri = withContext(Dispatchers.IO) {
        if (format == ContainerFormat.M4A) return@withContext input
        require(format == ContainerFormat.MP3) { "Solo podemos extraer audio real en MP3 o M4A." }

        val outputFile = File.createTempFile("snapmusic-audio-", ".mp3", workDir)
        executeOrThrow(
            buildString {
                append("-y -i ")
                append(ffmpegPath(input.toFile().absolutePath))
                append(" -map 0:a:0 -vn -c:a libmp3lame -b:a ")
                append("${normalizeBitrate(quality)}k")
                append(" -id3v2_version 3 ")
                append(ffmpegPath(outputFile.absolutePath))
            },
        )
        Uri.fromFile(outputFile)
    }

    override suspend fun muxVideo(videoInput: Uri, audioInput: Uri, targetProfile: String): Uri = withContext(Dispatchers.IO) {
        val outputFile = File.createTempFile("snapmusic-video-", ".mp4", workDir)
        executeOrThrow(
            buildString {
                append("-y -i ")
                append(ffmpegPath(videoInput.toFile().absolutePath))
                append(" -i ")
                append(ffmpegPath(audioInput.toFile().absolutePath))
                append(" -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a ")
                append("${muxAudioBitrate(targetProfile)}k")
                append(" -movflags +faststart -shortest ")
                append(ffmpegPath(outputFile.absolutePath))
            },
        )
        Uri.fromFile(outputFile)
    }

    private fun executeOrThrow(command: String) {
        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode
        if (ReturnCode.isSuccess(returnCode)) return
        if (ReturnCode.isCancel(returnCode)) {
            error("La transcodificación fue cancelada.")
        }
        error(session.failStackTrace?.takeIf { it.isNotBlank() } ?: session.output.takeIf { it.isNotBlank() }
            ?: "FFmpegKit no pudo completar el proceso.")
    }

    private fun ffmpegPath(path: String): String = "\"${path.replace("\"", "\\\"")}\""

    private fun normalizeBitrate(raw: String): Int {
        return raw.filter(Char::isDigit).toIntOrNull()?.coerceIn(96, 320) ?: 320
    }

    private fun muxAudioBitrate(profile: String): Int {
        val quality = profile.filter(Char::isDigit).toIntOrNull() ?: return 192
        return when {
            quality >= 1080 -> 256
            quality >= 720 -> 192
            else -> 160
        }
    }
}
