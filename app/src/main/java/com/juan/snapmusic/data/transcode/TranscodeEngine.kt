package com.juan.snapmusic.data.transcode

import android.net.Uri
import com.juan.snapmusic.core.model.ContainerFormat

interface TranscodeEngine {
    suspend fun extractAudio(input: Uri, format: ContainerFormat, quality: String): Uri
    suspend fun muxVideo(videoInput: Uri, audioInput: Uri, targetProfile: String): Uri
}
