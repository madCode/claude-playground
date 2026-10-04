package com.app.jekyllposter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.app.jekyllposter.ui.PosterNavHost
import com.app.jekyllposter.ui.theme.PosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation passes it again; the nav host takes each share only once.
        val shared = postToStart(intent)
        addNewPostShortcut()
        setContent {
            PosterTheme {
                PosterNavHost((application as PosterApp).container, shared)
            }
        }
    }

    /**
     * Long-pressing the app's icon offers New post, straight into the editor. Added here rather
     * than in a shortcuts.xml, whose intent has to name the package literally, and the debug
     * build's differs. Pushed again only when its label changed (another language): each push
     * counts as a use for the launcher's ranking.
     */
    private fun addNewPostShortcut() {
        runCatching {
            val label = getString(R.string.shortcut_new_post)
            if (ShortcutManagerCompat.getDynamicShortcuts(this).any { it.id == NEW_POST_SHORTCUT && it.shortLabel == label }) return
            val shortcut = ShortcutInfoCompat.Builder(this, NEW_POST_SHORTCUT)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_new_post))
                .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_NEW_POST))
                .build()
            // Never worth failing to start over: a launcher without shortcuts just doesn't show it.
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        }
    }

    companion object {
        /** The launcher's New post shortcut: opens an empty post. */
        const val ACTION_NEW_POST = "com.app.jekyllposter.NEW_POST"
        const val NEW_POST_SHORTCUT = "new-post"
    }
}

/**
 * The post [intent] starts: empty for the launcher's New post, or what another app shared.
 * Null otherwise, and when reopened from Recents, whose intent is the one that first started the
 * task, not a new share.
 */
internal fun postToStart(intent: Intent?): Shared? {
    if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    if (intent.action == MainActivity.ACTION_NEW_POST) return Shared("", emptyList())
    // "Open with": the note to import is the intent's data, read like a shared file.
    if (intent.action == Intent.ACTION_VIEW) return intent.data?.let { Shared("", emptyList(), it) }
    if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return null
    val streams = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.parcelable<Uri>(Intent.EXTRA_STREAM))
        else -> intent.parcelables<Uri>(Intent.EXTRA_STREAM)
    }
    val images = streams.filter { intent.type?.startsWith("image/") == true }
    // A note shared as a file (Markdown, or plain text), as Obsidian and file managers share them.
    val note = streams.singleOrNull()?.takeIf { intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true }
    val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        // A shared link becomes a Markdown link, titled by the page when the sharing app says it.
        if (subject != null && text.trim().startsWith("http") && !text.trim().contains(' ')) "[$subject](${text.trim()})\n" else text
    }
    if (note != null) return Shared(text.orEmpty(), emptyList(), note)
    return if (text == null && images.isEmpty()) null else Shared(text.orEmpty(), images)
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)

private inline fun <reified T : android.os.Parcelable> Intent.parcelables(key: String): List<T> =
    (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableArrayListExtra(key)).orEmpty()

/**
 * What to start a post with: text or a link and photos another app shared, a note shared as a
 * file (read in place of [text]), or nothing (New post).
 */
data class Shared(val text: String, val images: List<Uri>, val note: Uri? = null)
