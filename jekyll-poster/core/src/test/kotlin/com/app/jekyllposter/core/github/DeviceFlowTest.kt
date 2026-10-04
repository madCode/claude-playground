package com.app.jekyllposter.core.github

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DeviceFlowTest {
    private val server = MockWebServer()
    private val waits = mutableListOf<Long>()

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private val flow by lazy { DeviceFlow(OkHttpClient(), "Iv1.abc", server.url("/")) }

    private fun reply(json: String) = server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body(json).build())

    @Test fun theWriterApprovesAfterAWhileAndGitHubAsksToSlowDown() = runTest {
        reply("""{"device_code":"dc","user_code":"WDJB-MJHT","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""")
        val code = flow.start()
        assertEquals("WDJB-MJHT", code.userCode)
        assertTrue(server.takeRequest().body!!.utf8().contains("client_id=Iv1.abc"))

        reply("""{"error":"authorization_pending"}""")
        reply("""{"error":"slow_down","interval":10}""")
        reply("""{"access_token":"ghu_x","expires_in":28800,"refresh_token":"ghr_y","refresh_token_expires_in":15897600}""")
        val tokens = flow.await(code) { waits += it }
        assertEquals("ghu_x", tokens.accessToken)
        assertEquals("ghr_y", tokens.refreshToken)
        assertEquals(listOf(5000L, 5000L, 10000L), waits)
        val poll = server.takeRequest().body!!.utf8()
        assertTrue(poll.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
    }

    @Test fun aDeniedOrExpiredCodeSaysSo() = runTest {
        val code = DeviceFlow.Code("dc", "X", "u", 900, 1)
        reply("""{"error":"access_denied"}""")
        try { flow.await(code) {}; fail() } catch (e: GitHubException) { assertEquals(GitHubException.Kind.Unauthorized, e.kind) }
        reply("""{"error":"expired_token"}""")
        try { flow.await(code) {}; fail() } catch (e: GitHubException) { assertTrue(e.message!!.contains("expired")) }
    }

    @Test fun refreshingUsesTheRefreshToken() = runTest {
        reply("""{"access_token":"ghu_new","expires_in":28800,"refresh_token":"ghr_new"}""")
        assertEquals("ghu_new", flow.refresh("ghr_old").accessToken)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("grant_type=refresh_token") && body.contains("refresh_token=ghr_old"))
        reply("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""")
        try { flow.refresh("ghr_old"); fail() } catch (e: GitHubException) { assertEquals(GitHubException.Kind.Unauthorized, e.kind) }
    }
}
