package com.app.jekyllposter.core.webmention

import com.app.jekyllposter.core.io.readAtMost
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

/**
 * The webmentions [webmention.io](https://webmention.io) has received for a page, read from its
 * public API: anyone can ask for a page's, with no token, so the app sends none.
 */
class WebmentionIo(private val http: Call.Factory, private val base: HttpUrl = "https://webmention.io/".toHttpUrl()) {
    enum class Kind { Reply, Like, Repost, Bookmark, Rsvp, Mention }

    /** One mention: who, what kind, and where it is. [text] is a reply's, cut short. */
    data class Received(val kind: Kind, val author: String?, val url: String?, val text: String?, val received: String?)

    /**
     * The mentions of [target], newest first, at most [MAX] of them. Throws [IOException] when
     * webmention.io can't be reached or answers with something other than its JSON.
     */
    suspend fun mentions(target: String): List<Received> {
        val url = base.newBuilder().addPathSegments("api/mentions.jf2")
            .addQueryParameter("target", target)
            .addQueryParameter("per-page", MAX.toString())
            .addQueryParameter("sort-dir", "down")
            .build()
        val body = execute(Request.Builder().url(url).build()) { response ->
            if (!response.isSuccessful) throw IOException("webmention.io answered ${response.code}")
            val bytes = response.body.byteStream().readAtMost(MAX_ANSWER)
            if (bytes.size > MAX_ANSWER) throw IOException("webmention.io's answer is too large")
            bytes.decodeToString()
        }
        val feed = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw IOException("webmention.io's answer isn't its JSON")
        return (feed["children"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::received) }.take(MAX)
    }

    private fun received(entry: JsonObject): Received? {
        if (entry.string("wm-private") == "true") return null
        val kind = when (entry.string("wm-property")) {
            "in-reply-to" -> Kind.Reply
            "like-of" -> Kind.Like
            "repost-of" -> Kind.Repost
            "bookmark-of" -> Kind.Bookmark
            "rsvp" -> Kind.Rsvp
            else -> Kind.Mention
        }
        val author = (entry["author"] as? JsonObject)?.string("name")?.let(::plain)?.let { cut(it, 80) }
        // Opened in the browser when tapped, so only a web address is kept.
        val url = listOf("url", "wm-source").firstNotNullOfOrNull { key -> entry.string(key)?.takeIf { it.toHttpUrlOrNull() != null } }
        val text = ((entry["content"] as? JsonObject)?.string("text") ?: entry.string("summary"))
            ?.let(::plain)?.takeIf { kind == Kind.Reply || kind == Kind.Mention }
            ?.let { if (it.length > MAX_TEXT) cut(it, MAX_TEXT).trimEnd() + "…" else it }
        return Received(kind, author, url, text, entry.string("published") ?: entry.string("wm-received"))
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /**
     * One line of text, as anyone on the web may have written it: control characters and the
     * marks that reorder text (bidi overrides) go, and runs of whitespace become one space.
     */
    private fun plain(s: String): String? = s.replace(hidden, "").replace(spaces, " ").trim().ifEmpty { null }

    /** [s] in at most [n] characters, not splitting an emoji's or other character's two halves. */
    private fun cut(s: String, n: Int): String = if (s.length <= n) s else s.take(if (s[n - 1].isHighSurrogate()) n - 1 else n)

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                runCatching { response.use(read) }.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
            }
        })
    }

    companion object {
        /** No more mentions than this are read for a post. */
        const val MAX = 100
        private const val MAX_TEXT = 280
        private const val MAX_ANSWER = 2 * 1024 * 1024
        private val hidden = Regex("[\\u0000-\\u0008\\u000E-\\u001F\\u007F-\\u009F\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
        private val spaces = Regex("\\s+")
    }
}
