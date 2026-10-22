package com.app.jekyllposter.publish

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.app.jekyllposter.PosterApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Wakes the app when a post's send time comes. WorkManager's delay alone can be held back for
 * hours on a phone left idle (Doze, app standby buckets); an alarm allowed while idle comes
 * within minutes. It sends the post itself, and starts its work too, which stays the backstop.
 * Not an exact alarm: those need a permission Android denies by default.
 */
class SendTimeAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it >= 0 } ?: return
        val container = (context.applicationContext as PosterApp).container
        container.publishQueue.retryNow(id, null)
        val pending = goAsync()
        container.appScope.launch {
            // A receiver gets about ten seconds; the publish carries on in the app's scope after,
            // and the worker finds the post sent if it gets there first. Without the VPN it asked
            // for, it's left to the worker, which waits for it.
            val publishing = launch {
                val blog = container.drafts.get(id)?.blog ?: container.accounts.currentKey()
                if (!container.waitingForVpn(blog).first()) container.publisher.publish(id)
            }
            try {
                withTimeoutOrNull(8.seconds) { publishing.join() }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "draft"

        /** Wakes the app at [at] (epoch millis) to send post [id]; replaces any earlier alarm for it. */
        fun set(context: Context, id: Long, at: Long) {
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context, id))
        }

        fun cancel(context: Context, id: Long) = context.getSystemService(AlarmManager::class.java).cancel(intent(context, id))

        // One per post: the request code tells them apart, so setting one doesn't replace another's.
        private fun intent(context: Context, id: Long) = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, SendTimeAlarm::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
