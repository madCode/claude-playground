package com.app.jekyllposter.debug

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * Debug builds only: every request the app sends, as it goes on the wire, one JSON line each in
 * `files/requests/requests.jsonl`, for auditing what each sign-in sends. Off until the file
 * `files/requests/on` exists, since debug builds are also used for real:
 *
 *     adb shell run-as com.app.jekyllposter.debug mkdir -p files/requests
 *     adb shell run-as com.app.jekyllposter.debug touch files/requests/on
 *     adb shell run-as com.app.jekyllposter.debug cat files/requests/requests.jsonl
 *
 * No secret is written. The `Authorization` header is `(hidden)` and its token is named by a
 * fingerprint instead, so the requests of two sign-ins can be told apart; the device flow's
 * codes and a link's token parameters are hidden too, and anything shaped like a GitHub token
 * is hidden wherever it appears.
 */
class RequestRecord(dir: File) {
    val dir: File = dir
    val file = File(dir, "requests.jsonl")
    private val on = File(dir, "on")

    /** Records the requests of a client, labelled [via]: the blog they're for, or what else. */
    fun interceptor(via: String) = Interceptor { chain ->
        if (!on.exists()) return@Interceptor chain.proceed(chain.request())
        val request = chain.request()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            record(via, request, "failed: ${e.javaClass.simpleName}")
            throw e
        }
        record(via, request, response.code.toString())
        response
    }

    // Never fails the call: GitHub has already acted on it, and a lost answer can lose a sign-in
    // (a refresh token is spent once used).
    private fun record(via: String, request: Request, outcome: String) {
        runCatching { write(entry(via, request, outcome)) }
    }

    private fun entry(via: String, request: Request, outcome: String): JsonObject = buildJsonObject {
        put("at", Instant.now().toString())
        put("via", via)
        put("method", request.method)
        put("url", url(request.url))
        request.header("Authorization")?.let { put("token", fingerprint(it)) }
        putJsonObject("headers") {
            request.headers.names().forEach { name ->
                put(name, if (name.lowercase() in hiddenHeaders) HIDDEN else request.headers.values(name).joinToString(", "))
            }
        }
        body(request)?.let { put("body", it) }
        put("outcome", outcome)
    }

    /** A link to a private file carries a token in its query (`?token=…`) or user info. */
    private fun url(url: HttpUrl): String {
        val shown = url.newBuilder().username("").password("")
        url.queryParameterNames.filter { name -> secretParams.any { name.lowercase().contains(it) } }
            .forEach { shown.setQueryParameter(it, HIDDEN) }
        // Readable rather than percent-encoded.
        return shown.build().toString().replace("%28hidden%29", HIDDEN)
    }

    private fun body(request: Request): String? {
        val body = request.body ?: return null
        if (body is FormBody) {
            return (0 until body.size).joinToString("&") { i ->
                body.name(i) + "=" + if (body.name(i) in hiddenFields) HIDDEN else body.value(i)
            }
        }
        val length = body.contentLength()
        val type = body.contentType()
        val text = type?.type == "text" || type?.subtype?.contains("json") == true
        // A photo's blob runs to megabytes; its size says enough.
        if (!text || length !in 0..MAX_BODY || body.isOneShot() || body.isDuplex()) return "($length bytes, ${type ?: "no type"})"
        return Buffer().also { body.writeTo(it) }.readUtf8()
    }

    @Synchronized
    private fun write(entry: JsonObject) {
        dir.mkdirs()
        if (file.length() > MAX_FILE) file.renameTo(File(dir, "requests.1.jsonl"))
        file.appendText(scrub(entry.toString()) + "\n")
    }

    companion object {
        const val HIDDEN = "(hidden)"
        private const val MAX_BODY = 64L * 1024
        private const val MAX_FILE = 4L * 1024 * 1024
        private val hiddenHeaders = setOf("authorization", "cookie", "proxy-authorization")
        private val hiddenFields = setOf("device_code", "refresh_token", "code", "client_secret")
        private val secretParams = listOf("token", "sig", "secret", "key", "auth", "credential", "password")

        // GitHub's token shapes: classic, OAuth, user-to-server, server-to-server, refresh, fine-grained.
        // No word boundary before it: a token can follow `%3D` or `_` in a link.
        private val tokenShaped = Regex("""(gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,})""")

        fun scrub(text: String) = tokenShaped.replace(text, HIDDEN)

        /** Names a token without giving it away: eight hex digits of its SHA-256. */
        fun fingerprint(authorization: String): String {
            val token = authorization.substringAfter(' ').trim()
            val hash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            return "sha256:" + hash.take(4).joinToString("") { "%02x".format(it) }
        }

        @Volatile private var shared: RequestRecord? = null

        /** One record per app, so writes from every client take turns. */
        fun of(context: Context): RequestRecord =
            shared ?: synchronized(this) { shared ?: RequestRecord(File(context.filesDir, "requests")).also { shared = it } }
    }
}

/** [this] client, with its requests recorded as [via] when the record is on. */
fun OkHttpClient.recordedAs(context: Context, via: String): OkHttpClient =
    newBuilder().addNetworkInterceptor(RequestRecord.of(context).interceptor(via)).build()
