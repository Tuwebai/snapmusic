package com.juan.snapmusic.data.transcode

import android.net.Uri
import com.juan.snapmusic.core.model.ContainerFormat

class NoOpTranscodeEngine : TranscodeEngine {
    override suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String): Uri {
        if (format == ContainerFormat.M4A) return input
        error("La transcodificación avanzada todavía no está conectada al bundle local de FFmpegKit.")
    }

    override suspend fun muxVideo(videoInput: Uri, audioInput: Uri, targetProfile: String): Uri {
        error("El mux de video todavía no está conectado al bundle local de FFmpegKit.")
    }
}
