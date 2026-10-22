package com.app.jekyllposter.publish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.jekyllposter.MainActivity
import com.app.jekyllposter.R
import com.app.jekyllposter.data.BlogEntry
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft

/** Tells the writer when a post is live on the site, or why it isn't. */
class Notifier(private val context: Context) {
    /**
     * [elsewhere] is the post's blog when another one is open: the notification names it, since
     * "your site" would read as the open one.
     */
    fun buildFinished(draft: Draft, elsewhere: BlogEntry? = null) {
        val blog = elsewhere?.name
        val (title, text) = when (draft.buildState) {
            BuildState.Live if draft.hidden -> "“${draft.title}” is off ${blog ?: "your site"}" to "Its file stays on GitHub, marked “published: false”."
            BuildState.Live -> "“${draft.title}” is live${blog?.let { " on $it" }.orEmpty()}" to "Tap to open it on ${blog ?: "your site"}."
            BuildState.Failed -> "${blog ?: "Your site"} didn't rebuild" to "“${draft.title}” is on GitHub, but the Pages build failed. Check Actions on GitHub."
            else -> return
        }
        // What a locked phone shows instead: no title, which says what the writer writes about, and
        // no blog, which can say who the writer is.
        val public = when {
            draft.buildState != BuildState.Live -> "Your site didn't rebuild"
            draft.hidden -> "Your site rebuilt"
            else -> "A post is live"
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Published posts", NotificationManager.IMPORTANCE_DEFAULT))
        // A live post opens on the site, whichever blog the app has open. Otherwise the app opens,
        // on the post's blog when another is open: Home lists the post only under its own.
        val open = draft.postUrl?.takeIf { draft.buildState == BuildState.Live && !draft.hidden }
            ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            ?: elsewhere?.let { MainActivity.openBlog(context, it.key) }
            ?: Intent(context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(context, draft.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(public)
                    .build(),
            )
            .build()
        NotificationManagerCompat.from(context).notify(draft.id.toInt(), notification)
    }

    private companion object {
        const val CHANNEL = "published"
    }
}
