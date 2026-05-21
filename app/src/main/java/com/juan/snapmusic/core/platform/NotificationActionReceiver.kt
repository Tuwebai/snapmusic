package com.juan.snapmusic.core.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juan.snapmusic.SnapMusicApplication

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        private const val ACTION_CANCEL_QUEUE = "com.juan.snapmusic.CANCEL_QUEUE"
        private const val EXTRA_QUEUE_ID = "extra_queue_id"

        fun buildCancelPendingIntent(context: Context, queueId: String): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_CANCEL_QUEUE
                putExtra(EXTRA_QUEUE_ID, queueId)
            }
            return PendingIntent.getBroadcast(
                context,
                queueId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_QUEUE) return
        val queueId = intent.getStringExtra(EXTRA_QUEUE_ID).orEmpty()
        if (queueId.isBlank()) return
        val app = context.applicationContext as SnapMusicApplication
        app.appGraph.downloadCoordinator.cancelByQueueId(queueId)
    }
}
