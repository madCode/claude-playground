package com.app.jekyllposter.ui.editor

import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Fetches what the preview loads (the blog's images, pictures linked from elsewhere) through the
 * app's own HTTP client. The WebView's own requests carry its user agent, which names the phone's
 * model and Android version, to GitHub and to any site an image is on; these say only "okhttp".
 */
class PreviewFetcher(private val client: OkHttpClient) {
    /**
     * The response for [url], or null for what the WebView should load itself (`data:` photos
     * from the phone). Never null for a web address: that would let the WebView fetch it after all.
     */
    fun fetch(url: String): WebResourceResponse? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body.bytes()
                if (!response.isSuccessful) return notFound()
                val type = response.header("Content-Type").orEmpty()
                WebResourceResponse(type.substringBefore(';').trim().ifEmpty { "application/octet-stream" }, null, ByteArrayInputStream(body))
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
