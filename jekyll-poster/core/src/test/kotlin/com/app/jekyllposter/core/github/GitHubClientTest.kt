package com.app.jekyllposter.core.github

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GitHubClientTest {
    @Test fun aTimedOutRequestStopsThenNotWhenGitHubGivesUp() = runBlocking {
        MockWebServer().use { server ->
            // Headers at once, then a body that takes half a minute.
            server.enqueue(MockResponse().setBody("{\"login\":\"sample\"}").throttleBody(1, 30, TimeUnit.SECONDS))
            val client = GitHubClient(OkHttpClient(), "token", server.url("/"))
            val started = System.currentTimeMillis()
            assertNull(withTimeoutOrNull(300) { client.user() })
            val took = System.currentTimeMillis() - started
            assertTrue("Took $took ms", took < 5_000)
        }
    }
}
