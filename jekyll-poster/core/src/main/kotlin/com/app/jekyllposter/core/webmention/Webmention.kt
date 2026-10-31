package com.app.jekyllposter.core.webmention

import com.app.jekyllposter.core.io.readAtMost
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup

/**
 * Tells the pages a post links to that it does, by [Webmention](https://www.w3.org/TR/webmention/):
 * find each page's endpoint, then POST the post's address and the page's to it. The receiver
 * fetches the post to check the link is there, so this is only worth doing once the post is live.
 */
class Webmention(
    /**
     * Must not follow redirects itself: each hop is checked here first, so a page can't redirect
     * the app onto the writer's own network.
     */
    private val http: Call.Factory,
    /** How long one target may take, page and endpoint together. */
    private val timeLimit: Duration = 60.seconds,
) {
    /** How sending to one target went. */
    enum class Outcome {
        /** The endpoint took it (2xx). */
        Sent,

        /** The page names no endpoint, or can't be read as one: nothing to tell. */
        NoEndpoint,

        /** The endpoint answered 4xx, or the page or endpoint isn't one the app sends to. */
        Refused,
    }

    /**
     * Finds [target]'s endpoint and sends it [source]. Throws [IOException] when the page or the
     * endpoint couldn't be reached, was too slow, or said to try later, which is worth another go.
     */
    suspend fun send(source: String, target: String): Outcome = try {
        // OkHttp's timeouts are per read: a page sending a byte a minute would hold every target
        // after it until WorkManager stops the worker.
        withTimeout(timeLimit) { sendNow(source, target) }
    } catch (e: TimeoutCancellationException) {
        throw IOException("$target took too long", e)
    } catch (e: NotSent) {
        Outcome.Refused
    }

    private suspend fun sendNow(source: String, target: String): Outcome {
        val page = target.toHttpUrlOrNull()?.takeIf { sendsTo(it) } ?: return Outcome.Refused
        val endpoint = discover(page) ?: return Outcome.NoEndpoint
        if (!sendsTo(endpoint)) return Outcome.Refused
        val body = FormBody.Builder().add("source", source).add("target", target).build()
        return follow(Request.Builder().url(endpoint).post(body).build()) { response ->
            when {
                response.isSuccessful -> Outcome.Sent
                tryLater(response.code) -> throw IOException("The endpoint answered ${response.code}")
                else -> Outcome.Refused
            }
        }
    }

    /**
     * [page]'s endpoint, as the spec finds it: a `Link` header with `rel="webmention"` first, then
     * the first `<link>` or `<a>` in the page with that rel, resolved against the address the page
     * was served from after redirects. An empty `href` is the page itself.
     */
    internal suspend fun discover(page: HttpUrl): HttpUrl? = follow(Request.Builder().url(page).header("Accept", "text/html").build()) { response ->
        if (tryLater(response.code)) throw IOException("The page answered ${response.code}")
        if (!response.isSuccessful) return@follow null
        val base = response.request.url
        fromLinkHeaders(response.headers("Link"))?.let { return@follow base.resolve(it) }
        val type = response.body.contentType()
        if (type != null && type.subtype != "html" && type.subtype != "xhtml+xml") return@follow null
        val bytes = response.body.byteStream().readAtMost(MAX_PAGE)
        // A page too large to read whole is read as far as the limit: the endpoint is almost
        // always in the head. Without a charset in the header, jsoup reads the page's own
        // `<meta charset>` or byte order mark.
        Jsoup.parse(ByteArrayInputStream(bytes, 0, minOf(bytes.size, MAX_PAGE)), type?.charset()?.name(), base.toString())
            .select("link[href], a[href]")
            .firstOrNull { el -> el.attr("rel").split(whitespace).any { it.equals("webmention", ignoreCase = true) } }
            ?.let { base.resolve(it.attr("href").trim()) }
    }

    /** What one request led to: another address to go to, or what [follow]'s caller read. */
    private sealed interface Hop<out T> {
        class To(val url: HttpUrl) : Hop<Nothing>
        class Done<T>(val value: T) : Hop<T>
    }

    /**
     * Sends [request], following redirects itself: each address is checked with [sendsTo]
     * before anything goes to it. A POST follows only 307 and 308, which keep it a POST.
     */
    private suspend fun <T> follow(request: Request, read: (Response) -> T): T {
        var next = request
        repeat(MAX_HOPS + 1) {
            when (val hop = execute(next) { response -> hop(response, read) }) {
                is Hop.Done -> return hop.value
                is Hop.To -> {
                    if (!sendsTo(hop.url)) throw NotSent()
                    next = next.newBuilder().url(hop.url).build()
                }
            }
        }
        throw NotSent()
    }

    private fun <T> hop(response: Response, read: (Response) -> T): Hop<T> {
        // A client that followed a redirect itself went somewhere unchecked. OkHttp's own retry of
        // a 408 or a 503 is a response before this one too, but to the same address.
        if (generateSequence(response.priorResponse) { it.priorResponse }.any { it.isRedirect }) throw NotSent()
        val redirects = if (response.request.method == "GET") setOf(301, 302, 303, 307, 308) else setOf(307, 308)
        val to = response.header("Location")?.takeIf { response.code in redirects }?.let { response.request.url.resolve(it) }
        return if (to != null) Hop.To(to) else Hop.Done(read(response))
    }

    /** The page or endpoint isn't one the app sends to. */
    private class NotSent : Exception()

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
        /** No more of a page than this is read, looking for its endpoint. */
        private const val MAX_PAGE = 1024 * 1024

        /** No post tells more pages than this: a link list longer than it is rare, and slow to send. */
        const val MAX_TARGETS = 50

        private const val MAX_HOPS = 5

        /** Server trouble, too many requests, or a timeout: worth trying again later, unlike other 4xx. */
        private fun tryLater(code: Int) = code >= 500 || code == 429 || code == 408

        private val whitespace = Regex("\\s+")
        private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()
        private val renderer = HtmlRenderer.builder().extensions(listOf(TablesExtension.create())).escapeHtml(false).build()

        /**
         * The pages a post's Markdown [body] links to, to tell: absolute `http` and `https` links,
         * in Markdown or the post's own HTML, in order and each once, but none on the post's own
         * site, [site]'s host (with or without `www.`). Links made with Liquid (`post_url`,
         * `relative_url`) are the site's.
         */
        fun targets(body: String, site: String): List<String> {
            val own = site.toHttpUrlOrNull()?.host?.removePrefix("www.")
            return Jsoup.parse(renderer.render(parser.parse(body))).select("a[href]")
                .map { it.attr("href").trim() }
                .filter { href -> href.toHttpUrlOrNull()?.let { it.host.removePrefix("www.") != own && sendsTo(it) } == true }
                // A page is one target however its fragment points into it, and with or without
                // the slash after its host; it's sent as first written.
                .map { href -> href.substringBefore('#') }
                .distinctBy { it.toHttpUrlOrNull().toString() }
                .take(MAX_TARGETS)
        }

        /**
         * Whether the app sends to [url] at all: a page or endpoint on the phone itself or its
         * local network (named by its address, or `localhost`) is refused, so a page can't point
         * the app at the writer's router. A name that looks up to such an address isn't caught.
         */
        internal fun sendsTo(url: HttpUrl): Boolean {
            val host = url.host.trimEnd('.')
            if (host == "localhost" || host.endsWith(".localhost")) return false
            // OkHttp gives an IPv6 address without its brackets.
            if (host.contains(':')) return runCatching { InetAddress.getByName(host) }.getOrNull()?.let { isPublic(it.address) } ?: false
            // A name ending in a number is an address to the phone's resolver, which reads 127.1,
            // 2130706433 and 0x7f.1 as 127.0.0.1. Only the plain dotted form is judged; others go.
            val last = host.substringAfterLast('.')
            if (!last.all { it.isDigit() } && !last.startsWith("0x")) return true
            if (!dottedQuad.matches(host)) return false
            return isPublic(host.split('.').map { it.toInt().toByte() }.toByteArray())
        }

        private val dottedQuad = Regex("""(0|[1-9]\d{0,2})(\.(0|[1-9]\d{0,2})){3}""")

        /** Whether [address] (4 or 16 bytes) is out on the internet, not a local, private or special one. */
        private fun isPublic(address: ByteArray): Boolean {
            val b = address.map { it.toInt() and 0xff }
            if (b.size == 4) return when {
                b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 -> false
                b[0] == 100 && b[1] in 64..127 -> false
                b[0] == 169 && b[1] == 254 -> false
                b[0] == 172 && b[1] in 16..31 -> false
                b[0] == 192 && b[1] == 168 -> false
                b[0] == 192 && b[1] == 0 && b[2] == 0 -> false
                b[0] == 198 && b[1] in 18..19 -> false
                else -> true
            }
            // IPv4 inside IPv6: compatible (::a.b.c.d, and :: and ::1), mapped, NAT64 and 6to4.
            if (b.subList(0, 12).all { it == 0 }) return isPublic(address.copyOfRange(12, 16))
            if (b.subList(0, 10).all { it == 0 } && b[10] == 0xff && b[11] == 0xff) return isPublic(address.copyOfRange(12, 16))
            if (b[0] == 0x00 && b[1] == 0x64 && b[2] == 0xff && b[3] == 0x9b && b.subList(4, 12).all { it == 0 }) return isPublic(address.copyOfRange(12, 16))
            if (b[0] == 0x20 && b[1] == 0x02) return isPublic(address.copyOfRange(2, 6))
            return when {
                b[0] == 0xff -> false // multicast
                (b[0] and 0xfe) == 0xfc -> false // unique local, fc00::/7
                b[0] == 0xfe && (b[1] and 0xc0) in setOf(0x80, 0xc0) -> false // link and site local
                else -> true
            }
        }

        /**
         * The endpoint a page's `Link` headers name, as written: each header may hold several
         * links, and a rel several values. Read in one pass, so a hostile header can't make it slow.
         */
        internal fun fromLinkHeaders(headers: List<String>): String? {
            for (header in headers) {
                var i = 0
                while (i < header.length) {
                    val open = header.indexOf('<', i).takeIf { it >= 0 } ?: break
                    val close = header.indexOf('>', open + 1).takeIf { it >= 0 } ?: break
                    // The link's parameters run to the next comma outside quotes.
                    var end = close + 1
                    var quoted = false
                    while (end < header.length && (quoted || header[end] != ',')) {
                        if (header[end] == '"') quoted = !quoted
                        end++
                    }
                    if (rel(header.substring(close + 1, end)).any { it.equals("webmention", ignoreCase = true) }) return header.substring(open + 1, close).trim()
                    i = end + 1
                }
            }
            return null
        }

        /** The values of `rel` in a link's parameters (`; rel="a b"; title="x; y"`). */
        private fun rel(params: String): List<String> {
            val parts = mutableListOf<String>()
            val part = StringBuilder()
            var quoted = false
            for (c in params) {
                if (c == '"') quoted = !quoted
                if (c == ';' && !quoted) { parts += part.toString(); part.clear() } else part.append(c)
            }
            parts += part.toString()
            val value = parts.firstOrNull { it.substringBefore('=').trim().equals("rel", ignoreCase = true) && it.contains('=') } ?: return emptyList()
            return value.substringAfter('=').trim().removeSurrounding("\"").split(whitespace).filter { it.isNotEmpty() }
        }
    }
}
