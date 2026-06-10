package com.juan.snapmusic.core.performance

import android.util.Log

object SnapMusicFeedPagingTelemetry {
    private const val TAG = "SnapMusicFeedPaging"

    fun lane(
        kind: String,
        session: String,
        cursor: String?,
        lane: String,
        round: Int,
        fetched: Int,
        added: Int,
        duplicates: Int,
        exhausted: Boolean,
        nextCursor: String?,
        durationMs: Long,
        error: String? = null,
    ) {
        val suffix = error?.let { " error=$it" }.orEmpty()
        val message = "event=lane kind=$kind session=$session cursor=${cursor.orEmpty()} " +
            "lane=$lane round=$round fetched=$fetched added=$added duplicates=$duplicates " +
            "exhausted=$exhausted nextCursor=${nextCursor.orEmpty()} durationMs=$durationMs$suffix"
        if (error == null) Log.d(TAG, message) else Log.w(TAG, message)
    }

    fun cursor(
        kind: String,
        session: String,
        cursor: String,
        added: Int,
        exhausted: Boolean,
    ) {
        Log.d(
            TAG,
            "event=cursor kind=$kind session=$session cursor=$cursor lane=page " +
                "added=$added duplicates=0 exhausted=$exhausted durationMs=0",
        )
    }

    fun loadMore(
        kind: String,
        session: String,
        cursor: String?,
        lane: String,
        added: Int,
        duplicates: Int,
        exhausted: Boolean,
        durationMs: Long,
        resultCursor: String? = null,
        error: String? = null,
    ) {
        val suffix = error?.let { " error=$it" }.orEmpty()
        val message = "event=load-more kind=$kind session=$session cursor=${cursor.orEmpty()} " +
            "lane=$lane added=$added duplicates=$duplicates exhausted=$exhausted " +
            "resultCursor=${resultCursor.orEmpty()} durationMs=$durationMs$suffix"
        if (error == null) Log.d(TAG, message) else Log.w(TAG, message)
    }
}
