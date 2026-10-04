package com.app.jekyllposter

import android.content.Intent
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
        val shared = if (savedInstanceState == null) sharedText(intent) else null
        setContent {
            PosterTheme {
                PosterNavHost((application as PosterApp).container, shared)
            }
        }
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        // A shared link becomes a Markdown link, titled by the page when the sharing app says it.
        return if (subject != null && text.trim().startsWith("http") && !text.trim().contains(' ')) "[$subject](${text.trim()})\n" else text
    }
}
