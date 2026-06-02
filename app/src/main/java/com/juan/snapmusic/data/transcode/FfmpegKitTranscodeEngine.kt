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

    override suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String, artwork: Uri?): Uri = withContext(Dispatchers.IO) {
        when (format) {
            ContainerFormat.MP3 -> {
                val outputFile = File.createTempFile("snapmusic-audio-", ".mp3", workDir)
                executeOrThrow(
                    buildString {
                        append("-y -i ")
                        append(ffmpegPath(input.toFile().absolutePath))
                        appendArtworkInput(artwork)
                        append(" -map 0:a:0 ")
                        appendArtworkMap(artwork)
                        append(" -c:a libmp3lame -b:a ")
                        append("${normalizeBitrate(quality)}k")
                        appendMp3ArtworkOptions(artwork)
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
                        appendArtworkInput(artwork)
                        append(" -map 0:a:0 ")
                        appendArtworkMap(artwork)
                        append(" -c:a aac -b:a ")
                        append("${normalizeBitrate(quality)}k")
                        appendM4aArtworkOptions(artwork)
                        append(" -movflags +faststart ")
                        append(ffmpegPath(outputFile.absolutePath))
                    },
                )
                Uri.fromFile(outputFile)
            }

            else -> error("Solo podemos extraer audio real en MP3 o M4A.")
        }
    }

    override suspend fun tagAudio(input: Uri, format: ContainerFormat, artwork: Uri?): Uri = withContext(Dispatchers.IO) {
        val cover = artwork ?: return@withContext input
        when (format) {
            ContainerFormat.MP3 -> {
                val outputFile = File.createTempFile("snapmusic-tagged-", ".mp3", workDir)
                executeOrThrow(
                    buildString {
                        append("-y -i ")
                        append(ffmpegPath(input.toFile().absolutePath))
                        appendArtworkInput(cover)
                        append(" -map 0:a:0 ")
                        appendArtworkMap(cover)
                        append(" -c:a copy")
                        appendMp3ArtworkOptions(cover)
                        append(" -id3v2_version 3 ")
                        append(ffmpegPath(outputFile.absolutePath))
                    },
                )
                Uri.fromFile(outputFile)
            }

            ContainerFormat.M4A -> {
                val outputFile = File.createTempFile("snapmusic-tagged-", ".m4a", workDir)
                executeOrThrow(
                    buildString {
                        append("-y -i ")
                        append(ffmpegPath(input.toFile().absolutePath))
                        appendArtworkInput(cover)
                        append(" -map 0:a:0 ")
                        appendArtworkMap(cover)
                        append(" -c:a copy")
                        appendM4aArtworkOptions(cover)
                        append(" -movflags +faststart ")
                        append(ffmpegPath(outputFile.absolutePath))
                    },
                )
                Uri.fromFile(outputFile)
            }

            else -> input
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

    private fun StringBuilder.appendArtworkInput(artwork: Uri?) {
        if (artwork == null) return
        append(" -i ")
        append(ffmpegPath(artwork.toFile().absolutePath))
    }

    private fun StringBuilder.appendArtworkMap(artwork: Uri?) {
        if (artwork == null) {
            append(" -vn")
        } else {
            append(" -map 1:v:0")
        }
    }

    private fun StringBuilder.appendMp3ArtworkOptions(artwork: Uri?) {
        if (artwork == null) return
        append(" -c:v mjpeg -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\"")
    }

    private fun StringBuilder.appendM4aArtworkOptions(artwork: Uri?) {
        if (artwork == null) return
        append(" -c:v mjpeg -disposition:v:0 attached_pic")
    }

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
