package com.juan.snapmusic.data.transcode

import android.net.Uri
import com.juan.snapmusic.core.model.ContainerFormat

class NoOpTranscodeEngine : TranscodeEngine {
    override suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String, artwork: Uri?): Uri {
        if (format == ContainerFormat.M4A || format == ContainerFormat.WEBM) return input
        error("La transcodificación avanzada todavía no está conectada al bundle local de FFmpegKit.")
    }

    override suspend fun tagAudio(input: Uri, format: ContainerFormat, artwork: Uri?): Uri = input

    override suspend fun muxVideo(videoInput: Uri, audioInput: Uri, targetProfile: String): Uri {
        error("El mux de video todavía no está conectado al bundle local de FFmpegKit.")
    }
}
