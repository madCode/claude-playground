package com.app.jekyllposter.publish

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.Mention
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.SAMPLE_BLOG
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.home.summary
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Once a post is live, the pages it links to are told, if its blog sends webmentions. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class WebmentionsTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    /** The web the post links to: pages under `/page`, each naming `/wm` as its endpoint. */
    private val web = MockWebServer()

    /** What the endpoint answers next, by target path; 202 when not set. */
    private val endpointAnswers = mutableMapOf<String, Int>()

    /** The webmentions the endpoint took, as `source target`. */
    private val received = mutableListOf<String>()

    @Before fun setUp() = runBlocking {
        web.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/wm" -> {
                    val form = request.body!!.utf8().split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
                    val code = endpointAnswers[form.getValue("target").substringAfter(web.port.toString())] ?: 202
                    if (code in 200..299) synchronized(received) { received += "${form["source"]} ${form["target"]}" }
                    MockResponse.Builder().code(code).build()
                }
                "/plain" -> MockResponse.Builder().body("<p>No endpoint here.</p>").build()
                else -> MockResponse.Builder().body("<link rel=\"webmention\" href=\"/wm\">").addHeader("Content-Type", "text/html").build()
            }
        }
        web.start()
        app.signIn()
        c.blogs.refresh()
        Unit
    }

    @After fun close() {
        web.close()
        app.github.close()
    }

    private fun page(path: String) = "http://news.example:${web.port}$path"

    /** Publishes a post with [body] and lets the site build it, as the build watcher would see it. */
    private fun publishLive(body: String, conclusion: String = "success", extra: String? = null): Long = runBlocking {
        val id = c.drafts.insert(Draft(blog = SAMPLE_BLOG, title = "A walk", body = body, extraFrontMatter = extra, state = PostState.Queued))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
        app.github.runs[c.drafts.get(id)!!.commitSha!!] = "completed" to conclusion
        assertTrue(c.buildWatcher.check(id, giveUp = false))
        id
    }

    private fun sendWebmentions(on: Boolean) = runBlocking { c.privacy.update(SAMPLE_BLOG) { it.copy(sendWebmentions = on) } }

    private fun mentions(id: Long) = runBlocking { c.drafts.get(id)!!.webmentions }

    @Test fun offNothingIsSent() {
        val id = publishLive("See [the news](${page("/page/a")}).")
        assertTrue(app.webmentionsQueued.isEmpty())
        assertTrue(mentions(id).isEmpty())
        assertEquals(0, web.requestCount)
    }

    @Test fun onEachLinkedPageIsToldOnceThePostIsLive() = runBlocking {
        sendWebmentions(true)
        val id = publishLive("See [the news](${page("/page/a")}), [more](${page("/plain")}) and [an older post of mine](https://sample.github.io/old/).")
        assertEquals(listOf(id), app.webmentionsQueued)
        // Noted before anything is sent, so a retry knows what's left.
        assertEquals(listOf(Mention(page("/page/a")), Mention(page("/plain"))), mentions(id))
        assertEquals(0, web.requestCount)

        assertTrue(c.webmentions.send(id))
        val postUrl = c.drafts.get(id)!!.postUrl!!
        assertEquals(listOf("$postUrl ${page("/page/a")}"), received)
        assertEquals(listOf(Mention.State.Sent, Mention.State.NoEndpoint), mentions(id).map { it.state })
        assertEquals("Webmentions: told news.example · 1 site doesn't take them", mentions(id).summary())
    }

    @Test fun aSiteThatIsDownIsToldOnTheNextTryAndTheOthersNotAgain() = runBlocking {
        sendWebmentions(true)
        endpointAnswers["/page/b"] = 503
        val id = publishLive("[A](${page("/page/a")}) and [B](${page("/page/b")})")
        assertFalse(c.webmentions.send(id))
        assertEquals(listOf(Mention.State.Sent, Mention.State.Waiting), mentions(id).map { it.state })

        endpointAnswers.remove("/page/b")
        assertTrue(c.webmentions.send(id))
        assertEquals(listOf(page("/page/a"), page("/page/b")), received.map { it.substringAfter(' ') })

        endpointAnswers["/page/c"] = 503
        val other = publishLive("[C](${page("/page/c")})")
        repeat(Webmentions.MAX_TRIES - 1) { assertFalse(c.webmentions.send(other)) }
        assertTrue(c.webmentions.send(other))
        assertEquals(listOf(Mention(page("/page/c"), Mention.State.Failed, tries = Webmentions.MAX_TRIES)), mentions(other))
    }

    @Test fun notForAPostThatIsNotLive() {
        sendWebmentions(true)
        val failed = publishLive("[A](${page("/page/a")})", conclusion = "failure")
        val hidden = publishLive("[A](${page("/page/a")})", extra = "published: false")
        assertEquals(BuildState.Failed, runBlocking { c.drafts.get(failed)!!.buildState })
        assertTrue(mentions(failed).isEmpty() && mentions(hidden).isEmpty())
        assertTrue(app.webmentionsQueued.isEmpty())
    }

    @Test fun aBuildANewerPushCancelledIsLikelyLiveAndIsSentFor() {
        sendWebmentions(true)
        val id = publishLive("[A](${page("/page/a")})", conclusion = "cancelled")
        assertEquals(BuildState.Unknown, runBlocking { c.drafts.get(id)!!.buildState })
        assertEquals(listOf(id), app.webmentionsQueued)
    }

    @Test fun theSameBuildReportedAgainSendsNothingTwice() = runBlocking {
        sendWebmentions(true)
        val id = publishLive("[A](${page("/page/a")})")
        assertTrue(c.webmentions.send(id))
        c.webmentions.built(c.drafts.get(id)!!)
        assertEquals(listOf(id), app.webmentionsQueued)
        assertEquals(listOf(Mention.State.Sent), mentions(id).map { it.state })
    }

    @Test fun turnedOffBeforeTheyGoNothingMoreIsSent() = runBlocking {
        sendWebmentions(true)
        val id = publishLive("[A](${page("/page/a")})")
        sendWebmentions(false)
        assertTrue(c.webmentions.send(id))
        assertEquals(0, web.requestCount)
        assertTrue(mentions(id).isEmpty())
    }

    @Test fun throughTheBlogsVpnSwitch() = runBlocking {
        sendWebmentions(true)
        val id = publishLive("[A](${page("/page/a")})")
        c.setOnlyThroughVpn(SAMPLE_BLOG, true)
        app.vpn.up.value = false
        // However long the VPN is away, waiting for it isn't a try.
        repeat(Webmentions.MAX_TRIES + 1) { assertFalse(c.webmentions.send(id)) }
        assertEquals(0, web.requestCount)
        assertEquals(listOf(Mention(page("/page/a"))), mentions(id))
    }

    @Test fun theLineSaysWhatHappened() {
        fun m(n: Int, state: Mention.State) = (1..n).map { Mention("https://site$it-${state.name.lowercase()}.example/x", state) }
        assertEquals(null, emptyList<Mention>().summary())
        assertEquals(
            "Webmentions: told site1-sent.example, site2-sent.example and 2 more · telling 1 site… · 2 sites couldn't be told",
            (m(4, Mention.State.Sent) + m(1, Mention.State.Waiting) + m(1, Mention.State.Refused) + m(1, Mention.State.Failed)).summary(),
        )
    }
}
