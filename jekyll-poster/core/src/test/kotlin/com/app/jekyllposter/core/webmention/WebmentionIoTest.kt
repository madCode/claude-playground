package com.app.jekyllposter.core.webmention

import com.app.jekyllposter.core.webmention.WebmentionIo.Kind
import com.app.jekyllposter.core.webmention.WebmentionIo.Received
import java.io.IOException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebmentionIoTest {
    private val server = MockWebServer()
    private lateinit var io: WebmentionIo

    @Before fun start() {
        server.start()
        io = WebmentionIo(OkHttpClient(), server.url("/"))
    }

    @After fun stop() = server.close()

    private fun answer(json: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(json).build())

    /** An entry as webmention.io writes one. */
    private fun entry(property: String, name: String = "Ada", url: String = "https://ada.example/notes/1", extra: String = "") = """
        {"type":"entry","author":{"type":"card","name":"$name","photo":"","url":"https://ada.example/"},"url":"$url",
         "published":"2026-10-20T09:00:00+00:00","wm-received":"2026-10-20T09:05:00Z","wm-id":1,
         "wm-source":"https://ada.example/notes/1","wm-target":"https://writer.example/a/","wm-property":"$property","wm-private":false$extra}
    """.trimIndent()

    @Test fun asksForThePostsMentionsWithNoToken() = runBlocking {
        answer("""{"type":"feed","name":"Webmentions","children":[],"paging":{"per-page":100,"page":0}}""")
        assertEquals(emptyList<Received>(), io.mentions("https://writer.example/2026/10/25/a walk/"))
        val asked = server.takeRequest().url
        assertEquals("/api/mentions.jf2", asked.encodedPath)
        assertEquals("https://writer.example/2026/10/25/a walk/", asked.queryParameter("target"))
        assertEquals("100", asked.queryParameter("per-page"))
        assertEquals(null, asked.queryParameter("token"))
    }

    @Test fun eachKindWithWhoAndWhere() = runBlocking {
        answer(
            """{"type":"feed","children":[
                ${entry("in-reply-to", extra = ""","content":{"html":"<p>Lovely</p>","text":"Lovely\n\nwalk!"}""")},
                ${entry("like-of", name = "Bo", extra = ""","content":{"text":"liked"}""")},
                ${entry("repost-of", name = "")},
                ${entry("bookmark-of")},
                ${entry("mention-of", url = "javascript:alert(1)", extra = ""","summary":"Linked from a list"""")},
                ${entry("rsvp")},
                ${entry("in-reply-to", extra = ""","wm-private":true""").replace(""","wm-private":false""", "")}
            ]}""",
        )
        assertEquals(
            listOf(
                Received(Kind.Reply, "Ada", "https://ada.example/notes/1", "Lovely walk!", "2026-10-20T09:00:00+00:00"),
                // A like's text is only "liked": nothing to show.
                Received(Kind.Like, "Bo", "https://ada.example/notes/1", null, "2026-10-20T09:00:00+00:00"),
                Received(Kind.Repost, null, "https://ada.example/notes/1", null, "2026-10-20T09:00:00+00:00"),
                Received(Kind.Bookmark, "Ada", "https://ada.example/notes/1", null, "2026-10-20T09:00:00+00:00"),
                // Not a web address, so the page that sent it stands in.
                Received(Kind.Mention, "Ada", "https://ada.example/notes/1", "Linked from a list", "2026-10-20T09:00:00+00:00"),
                Received(Kind.Rsvp, "Ada", "https://ada.example/notes/1", null, "2026-10-20T09:00:00+00:00"),
            ),
            io.mentions("https://writer.example/a/"),
        )
    }

    @Test fun whatAnyoneWroteIsShownAsOneShortLine() = runBlocking {
        val long = "word ".repeat(200)
        answer("""{"children":[${entry("in-reply-to", name = "‮evil\u0007", extra = ""","content":{"text":"$long"}""")}]}""")
        val reply = io.mentions("https://writer.example/a/").single()
        assertEquals("evil", reply.author)
        assertTrue(reply.text!!.length <= 281)
        assertTrue(reply.text.endsWith("…"))
    }

    @Test fun anAnswerThatIsNotItsJsonIsAFailure() {
        for ((body, code) in listOf("<html>Down for maintenance</html>" to 200, "{}" to 500, "[1,2]" to 200)) {
            answer(body, code)
            assertTrue(body, runCatching { runBlocking { io.mentions("https://writer.example/a/") } }.exceptionOrNull() is IOException)
        }
    }
}
