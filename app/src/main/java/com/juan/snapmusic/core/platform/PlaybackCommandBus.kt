package com.juan.snapmusic.core.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class PlaybackCommand {
    YOUTUBE_NEXT,
    YOUTUBE_PREVIOUS,
    YOUTUBE_PLAY_PAUSE,
}

object PlaybackCommandBus {
    private val _commands = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()

    fun dispatch(command: PlaybackCommand) {
        _commands.tryEmit(command)
    }
}
