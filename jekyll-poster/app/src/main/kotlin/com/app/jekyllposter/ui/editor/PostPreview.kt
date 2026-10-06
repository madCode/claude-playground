package com.app.jekyllposter.ui.editor
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun PostPreview(viewModel: EditorViewModel, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val html by produceState("", viewModel.text, dark) { value = viewModel.previewHtml(dark) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // The writer's own HTML, but still: no scripts, no file access.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // A tapped link opens in the browser, not in place of the preview.
                webViewClient = object : WebViewClient() {
                    // Runs off the main thread, as the WebView calls it.
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        viewModel.previewFetcher.fetch(request.url.toString())

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
        modifier = modifier.semantics { contentDescription = "Preview of the post" },
    )
}
