package com.app.jekyllposter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.app.jekyllposter.ui.PosterNavHost
import com.app.jekyllposter.ui.theme.PosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: a rotation would otherwise start a second post from the same share.
        val shared = if (savedInstanceState == null) shared(intent) else null
        setContent {
            PosterTheme {
                PosterNavHost((application as PosterApp).container, shared)
            }
        }
    }

    private fun shared(intent: Intent?): Shared? {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return null
        val images = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelable<Uri>(Intent.EXTRA_STREAM))
            else -> intent.parcelables<Uri>(Intent.EXTRA_STREAM)
        }.filter { intent.type?.startsWith("image/") == true }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            // A shared link becomes a Markdown link, titled by the page when the sharing app says it.
            if (subject != null && text.trim().startsWith("http") && !text.trim().contains(' ')) "[$subject](${text.trim()})\n" else text
        }
        return if (text == null && images.isEmpty()) null else Shared(text.orEmpty(), images)
    }

    private inline fun <reified T : android.os.Parcelable> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)

    private inline fun <reified T : android.os.Parcelable> Intent.parcelables(key: String): List<T> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java) else @Suppress("DEPRECATION") getParcelableArrayListExtra(key)).orEmpty()
}

/** What another app shared to start a post with: text or a link, and photos. */
data class Shared(val text: String, val images: List<Uri>)
