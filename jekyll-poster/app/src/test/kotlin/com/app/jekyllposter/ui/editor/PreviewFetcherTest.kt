package com.app.jekyllposter.ui.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewFetcherTest {
    private val server = MockWebServer().apply { start() }
    private val fetcher = PreviewFetcher(OkHttpClient())

    @After fun close() = server.close()

    @Test fun anImageIsFetchedWithoutNamingThePhone() {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").body("jpeg bytes").build())
        val response = fetcher.fetch(server.url("/assets/images/2025/lighthouse.jpg").toString())!!
        assertEquals("image/jpeg", response.mimeType)
        assertEquals("jpeg bytes", response.data.readBytes().toString(Charsets.UTF_8))
        val agent = server.takeRequest().headers["User-Agent"].orEmpty()
        // OkHttp's own, not the WebView's "Linux; Android 15; Pixel 9 …".
        assertTrue(agent, agent.startsWith("okhttp/"))
        assertTrue(agent, !agent.contains("Android"))
    }

    @Test fun aMissingImageIsAnEmptyAnswerNotALoadForTheWebView() {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(404, fetcher.fetch(server.url("/gone.jpg").toString())!!.statusCode)
        // Unreachable too: null would hand the load back to the WebView.
        assertEquals(404, fetcher.fetch("https://unreachable.invalid/x.jpg")!!.statusCode)
    }

    @Test fun photosFromThePhoneAreLeftToTheWebView() {
        assertNull(fetcher.fetch("data:image/jpeg;base64,AAAA"))
    }
}
