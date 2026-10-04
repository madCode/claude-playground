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
    private companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
    }

    /**
     * The response for [url], or null for what the WebView should load itself (`data:` photos
     * from the phone). Never null for a web address: that would let the WebView fetch it after all.
     */
    fun fetch(url: String): WebResourceResponse? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return notFound()
                // Images, not a video someone linked: anything bigger isn't read into memory.
                val length = response.body.contentLength()
                if (length > MAX_BYTES) return notFound()
                val body = response.body.source().use { source ->
                    if (!source.request(MAX_BYTES + 1) || source.buffer.size <= MAX_BYTES) source.readByteArray() else return notFound()
                }
                val type = response.header("Content-Type").orEmpty()
                WebResourceResponse(type.substringBefore(';').trim().ifEmpty { "application/octet-stream" }, null, ByteArrayInputStream(body))
            }
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
