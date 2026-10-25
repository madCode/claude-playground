package com.app.jekyllposter.core.webmention

import com.app.jekyllposter.core.webmention.Webmention.Outcome
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebmentionTest {
    private val server = MockWebServer()

    // Every name is the test server, so pages and endpoints have names as on the web: the app
    // refuses to send to `localhost` itself.
    private val http = OkHttpClient.Builder().followRedirects(false).dns(object : Dns {
        override fun lookup(hostname: String) = listOf(InetAddress.getLoopbackAddress())
    }).build()
    private val webmention = Webmention(http)

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun url(host: String, path: String) = "http://$host:${server.port}$path"

    private fun page(html: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().body(html).addHeader("Content-Type", "text/html; charset=utf-8").apply { headers.forEach { addHeader(it.first, it.second) } }.build()

    private fun endpointOf(response: MockResponse, path: String = "/post") = runBlocking {
        server.enqueue(response)
        webmention.discover(url("target.example", path).toHttpUrl())?.toString()
    }

    @Test fun theEndpointAsTheSpecFindsIt() {
        assertEquals(url("target.example", "/wm"), endpointOf(page("<p>hi</p>", "Link" to "</wm>; rel=\"webmention\"")))
        assertEquals("https://webmention.io/x/webmention", endpointOf(page("", "Link" to "<https://webmention.io/x/webmention>; rel=webmention")))
        // Several links in one header, and a rel with several values.
        assertEquals(url("target.example", "/b"), endpointOf(page("", "Link" to "</a>; rel=\"preload\", </b>; rel=\"other webmention\"")))
        // The header comes before the page.
        assertEquals(url("target.example", "/h"), endpointOf(page("<link rel=webmention href=/p>", "Link" to "</h>; rel=\"webmention\"")))
        assertEquals(url("target.example", "/p/wm"), endpointOf(page("<html><head><link rel=\"webmention\" href=\"wm\"></head></html>"), "/p/post"))
        // The first in the document, <a> or <link>; a rel that only contains the word isn't it.
        assertEquals(
            url("target.example", "/a"),
            endpointOf(page("<link rel=\"not-webmention\" href=\"/no\"><a rel=\"me Webmention\" href=\"/a\">x</a><link rel=webmention href=/l>")),
        )
        // An empty href is the page itself, and an endpoint's query is kept.
        assertEquals(url("target.example", "/post?x=1"), endpointOf(page("<link rel=webmention href=\"\">"), "/post?x=1"))
        assertEquals(url("target.example", "/wm?key=a&b=c"), endpointOf(page("<link rel=webmention href=\"/wm?key=a&amp;b=c\">")))
    }

    @Test fun noEndpoint() {
        assertNull(endpointOf(page("<p>Just a page</p>")))
        assertNull(endpointOf(page("<link rel=webmention>")))
        assertNull(endpointOf(MockResponse.Builder().body("<link rel=webmention href=/x>").addHeader("Content-Type", "application/pdf").build()))
        assertNull(endpointOf(MockResponse.Builder().code(404).body("<link rel=webmention href=/x>").build()))
    }

    @Test fun aRelativeEndpointIsResolvedWhereTheRedirectsEnded() = runBlocking {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", url("other.example", "/new/post")).build())
        server.enqueue(page("<link rel=webmention href=\"wm\">"))
        assertEquals(url("other.example", "/new/wm"), webmention.discover(url("target.example", "/post").toHttpUrl()).toString())
    }

    @Test fun sendsTheSourceAndTargetAsAForm() = runBlocking {
        val target = url("target.example", "/post")
        server.enqueue(page("<link rel=webmention href=\"http://hub.example:${server.port}/webmention\">"))
        server.enqueue(MockResponse.Builder().code(202).build())
        assertEquals(Outcome.Sent, webmention.send("https://writer.example/2026/10/25/hello/", target))
        server.takeRequest()
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("hub.example", sent.url.host)
        assertEquals("application/x-www-form-urlencoded", sent.headers["Content-Type"])
        assertEquals("source=https%3A%2F%2Fwriter.example%2F2026%2F10%2F25%2Fhello%2F&target=" + java.net.URLEncoder.encode(target, "UTF-8"), sent.body!!.utf8())
    }

    @Test fun whatTheEndpointSays() = runBlocking {
        val target = url("target.example", "/post")
        server.enqueue(page("<link rel=webmention href=/wm>"))
        server.enqueue(MockResponse.Builder().code(400).build())
        assertEquals(Outcome.Refused, webmention.send("https://writer.example/a/", target))

        server.enqueue(page("<p>none</p>"))
        assertEquals(Outcome.NoEndpoint, webmention.send("https://writer.example/a/", target))

        // Worth trying again: the endpoint or the page is down.
        server.enqueue(page("<link rel=webmention href=/wm>"))
        server.enqueue(MockResponse.Builder().code(503).build())
        assertTrue(runCatching { webmention.send("https://writer.example/a/", target) }.exceptionOrNull() is IOException)
        server.enqueue(MockResponse.Builder().code(502).build())
        assertTrue(runCatching { webmention.send("https://writer.example/a/", target) }.exceptionOrNull() is IOException)
    }

    @Test fun neverSendsToThePhoneOrItsNetwork() = runBlocking {
        server.enqueue(page("<link rel=webmention href=\"http://192.168.1.1/admin\">"))
        assertEquals(Outcome.Refused, webmention.send("https://writer.example/a/", url("target.example", "/post")))
        assertEquals(1, server.requestCount)
        assertEquals(Outcome.Refused, webmention.send("https://writer.example/a/", "http://localhost:${server.port}/post"))
        assertEquals(1, server.requestCount)
        for (host in listOf("127.0.0.1", "10.0.0.2", "172.16.4.1", "169.254.1.1", "0.0.0.0", "[::1]", "[fd00::1]", "[fe80::1]", "router.localhost")) {
            assertFalse(host, Webmention.sendsTo("http://$host/".toHttpUrl()))
        }
        for (host in listOf("8.8.8.8", "[2001:db8::1]", "example.com", "xn--bcher-kva.example")) {
            assertTrue(host, Webmention.sendsTo("https://$host/".toHttpUrl()))
        }
    }

    @Test fun theTargetsAreThePostsOutsideLinks() {
        val body = """
            A [walk](https://a.example/walk#map) and [the same walk](https://a.example/walk), then
            <a href="https://b.example/">a raw link</a>, <https://c.example/auto>, and
            [my own post](https://writer.example/2026/01/01/old/), [about]({{ '/about/' | relative_url }}),
            [mail](mailto:me@example.com), [phone](tel:123) and [a relative one](/x).

            `https://d.example/in-code` isn't a link. ![A photo](https://e.example/photo.jpg) isn't either.
        """.trimIndent()
        assertEquals(
            listOf("https://a.example/walk", "https://b.example/", "https://c.example/auto"),
            Webmention.targets(body, "https://writer.example/2026/10/25/hello/"),
        )
    }

    @Test fun aLongLinkListIsCut() {
        val body = (1..80).joinToString("\n") { "- [$it](https://example.com/$it)" }
        assertEquals(Webmention.MAX_TARGETS, Webmention.targets(body, "https://writer.example/").size)
    }

    @Test fun aHostileLinkHeaderIsReadQuickly() {
        val many = "<a>" + "; a".repeat(20_000)
        val angles = "<".repeat(200_000)
        val started = System.nanoTime()
        assertNull(Webmention.fromLinkHeaders(listOf(many, angles, "<x<x".repeat(50_000))))
        assertEquals("/wm", Webmention.fromLinkHeaders(listOf(many + ", </wm>; rel=webmention")))
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 2_000_000_000)
        // A quoted parameter may hold commas and semicolons.
        assertEquals("/b", Webmention.fromLinkHeaders(listOf("</a>; title=\"x, rel=webmention; y\", </b>; REL=\"webmention\"")))
    }

    @Test fun anAddressByAnyOtherNumberIsRefused() {
        for (host in listOf("2130706433", "127.1", "0x7f000001", "0177.0.0.1", "3232235777", "100.100.1.1", "198.18.0.1", "192.0.0.8", "255.255.255.255", "[::192.168.1.1]",
            "[64:ff9b::c0a8:101]", "[2002:c0a8:101::1]", "[::ffff:192.168.1.1]", "localhost.", "127.0.0.1.")) {
            assertFalse(host, Webmention.sendsTo("http://$host/".toHttpUrl()))
        }
        for (host in listOf("[2002:808:808::1]", "[64:ff9b::808:808]", "1.1.1.1", "100.128.0.1", "example.com.")) {
            assertTrue(host, Webmention.sendsTo("http://$host/".toHttpUrl()))
        }
    }

    @Test fun aRedirectOntoThePhonesNetworkIsNotFollowed() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://127.0.0.1:${server.port}/admin").build())
        assertEquals(Outcome.Refused, webmention.send("https://writer.example/a/", url("target.example", "/post")))
        server.enqueue(page("<link rel=webmention href=/wm>"))
        server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "http://192.168.1.1/admin").build())
        assertEquals(Outcome.Refused, webmention.send("https://writer.example/a/", url("target.example", "/post")))
        assertEquals(3, server.requestCount)
        // An endpoint that moved, with a 307, gets the POST at its new address.
        server.enqueue(page("<link rel=webmention href=/old>"))
        server.enqueue(MockResponse.Builder().code(307).addHeader("Location", "/new").build())
        server.enqueue(MockResponse.Builder().code(201).build())
        assertEquals(Outcome.Sent, webmention.send("https://writer.example/a/", url("target.example", "/post")))
        repeat(5) { server.takeRequest() }
        assertEquals("POST /new", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test fun aClientThatFollowsRedirectsItselfIsRefused() = runBlocking {
        val careless = Webmention(http.newBuilder().followRedirects(true).build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/elsewhere").build())
        server.enqueue(page("<link rel=webmention href=/wm>"))
        assertEquals(Outcome.Refused, careless.send("https://writer.example/a/", url("target.example", "/post")))
    }

    @Test fun pagesTheWaySitesServeThem() {
        assertEquals(url("target.example", "/x"), endpointOf(MockResponse.Builder().body("<html><link rel=webmention href=/x></html>").addHeader("Content-Type", "application/xhtml+xml").build()))
        // A charset only in the page itself.
        val sjis = "<html><head><meta charset=\"Shift_JIS\"><link rel=webmention href=\"/\u65e5\"></head></html>".toByteArray(charset("Shift_JIS"))
        assertEquals(url("target.example", "/%E6%97%A5"), endpointOf(MockResponse.Builder().body(okio.Buffer().write(sjis)).addHeader("Content-Type", "text/html").build()))
        // Too many requests is worth trying again; not found isn't.
        // OkHttp tries a 408 again by itself; the second answer is the one that counts.
        server.enqueue(page("<link rel=webmention href=/wm>"))
        server.enqueue(MockResponse.Builder().code(408).build())
        server.enqueue(MockResponse.Builder().code(202).build())
        assertEquals(Outcome.Sent, runBlocking { webmention.send("https://writer.example/a/", url("target.example", "/post")) })
        server.enqueue(MockResponse.Builder().code(429).build())
        assertTrue(runCatching { runBlocking { webmention.send("https://writer.example/a/", url("target.example", "/post")) } }.exceptionOrNull() is IOException)
    }

    @Test fun aSlowPageIsGivenUpOnInTime() = runBlocking {
        val quick = Webmention(http, timeLimit = 1.seconds)
        server.enqueue(page("<p>" + "x".repeat(10_000) + "</p>").newBuilder().throttleBody(10, 1, TimeUnit.SECONDS).build())
        val started = System.nanoTime()
        assertTrue(runCatching { quick.send("https://writer.example/a/", url("target.example", "/post")) }.exceptionOrNull() is IOException)
        assertTrue(System.nanoTime() - started < 5_000_000_000)
    }

    @Test fun aPageIsOneTargetWithOrWithoutItsSlash() {
        assertEquals(
            listOf("https://a.example"),
            Webmention.targets("[a](https://a.example) [b](https://a.example/) [mine](https://www.writer.example/x)", "https://writer.example/post/"),
        )
    }
}
