package com.juan.snapmusic.data.transcode

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.juan.snapmusic.core.model.ContainerFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FfmpegKitTranscodeEngine(
    context: Context,
) : TranscodeEngine {
    private val workDir = File(context.cacheDir, "ffmpeg").apply { mkdirs() }

    override suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String): Uri = withContext(Dispatchers.IO) {
        when (format) {
            ContainerFormat.MP3 -> {
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

            ContainerFormat.M4A -> {
                val outputFile = File.createTempFile("snapmusic-audio-", ".m4a", workDir)
                executeOrThrow(
                    buildString {
                        append("-y -i ")
                        append(ffmpegPath(input.toFile().absolutePath))
                        append(" -map 0:a:0 -vn -c:a aac -b:a ")
                        append("${normalizeBitrate(quality)}k")
                        append(" -movflags +faststart ")
                        append(ffmpegPath(outputFile.absolutePath))
                    },
                )
                Uri.fromFile(outputFile)
            }

            else -> error("Solo podemos extraer audio real en MP3 o M4A.")
        }
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

    private suspend fun executeOrThrow(command: String) = suspendCancellableCoroutine { cont ->
        lateinit var session: FFmpegSession
        session = FFmpegKit.executeAsync(
            command,
            { completedSession ->
                val returnCode = completedSession.returnCode
                when {
                    ReturnCode.isSuccess(returnCode) -> cont.resume(Unit)
                    ReturnCode.isCancel(returnCode) -> cont.resumeWithException(
                        CancellationException("La transcodificación fue cancelada."),
                    )

                    else -> cont.resumeWithException(
                        IllegalStateException(
                            completedSession.failStackTrace?.takeIf { it.isNotBlank() }
                                ?: completedSession.output.takeIf { it.isNotBlank() }
                                ?: "FFmpegKit no pudo completar el proceso.",
                        ),
                    )
                }
            },
            null,
            null,
        )
        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
            FFmpegKitConfig.clearSessions()
        }
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
