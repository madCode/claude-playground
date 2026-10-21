package com.app.jekyllposter.update

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Notices when CI has published a newer debug build than the one installed, so the writer isn't
 * left on a stale one. Asks GitHub when the app comes to the front, at most every
 * [INTERVAL_MILLIS].
 *
 * @param installed this app's CI build number, or null for a release or locally built app, which
 *   never asks.
 * @param fetch the release's JSON, or null when GitHub answered with anything but 200; throws
 *   [IOException] when there was no answer at all, or the VPN the writer asked for isn't up.
 */
class DebugUpdates(
    private val prefs: SharedPreferences,
    private val installed: Int?,
    private val fetch: suspend () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val newerBuild = MutableStateFlow(newer(prefs.getInt(LATEST, 0)))

    /** The newest build's number while it's newer than this one, else null. */
    val newer: StateFlow<Int?> = newerBuild.asStateFlow()

    suspend fun checkIfDue() {
        if (installed == null) return
        // A clock set back counts as due, or it could hold the check off for as long.
        if (now() - prefs.getLong(CHECKED, 0L) in 0 until INTERVAL_MILLIS) return
        val body = try {
            fetch()
        } catch (_: IOException) {
            // Not counted: offline, or waiting for the VPN, it asks again next time.
            return
        }
        // Any answer counts, refusals too: retried on every return to the app, a rate limit
        // (60 an hour per address) would stay used up.
        prefs.edit().putLong(CHECKED, now()).apply()
        val latest = body?.let(::latestBuild) ?: return
        prefs.edit().putInt(LATEST, latest).apply()
        newerBuild.value = newer(latest)
    }

    private fun newer(latest: Int) = latest.takeIf { installed != null && it > installed }

    companion object {
        val INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(3)

        val RELEASE_API = "https://api.github.com/repos/madCode/claude-playground/releases/tags/jekyll-poster-debug".toHttpUrl()
        val DOWNLOAD = "https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk".toHttpUrl()

        /** Shared by the check on opening the app and the debug build's background check. */
        fun prefsOf(context: Context): SharedPreferences = context.getSharedPreferences("debug-updates", Context.MODE_PRIVATE)

        private const val CHECKED = "checked"
        private const val LATEST = "latest"

        // A release answer is a few kilobytes; anything far larger is not one, and is not read.
        private const val MAX_BODY = 256L * 1024

        // CI names each build's file after its run number, beside the fixed name DOWNLOAD links to.
        private val BUILD_FILE = Regex("""jekyll-poster-debug-(\d{1,9})\.apk""")
        private val BUILD_IN_VERSION = Regex("""-debug\.(\d{1,9})""")

        /** The newest build number among the release's files, from GitHub's release JSON. */
        fun latestBuild(releaseJson: String): Int? =
            BUILD_FILE.findAll(releaseJson).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()

        /** The CI build number in a version name like "0.1.0-debug.131+012eb32"; null without one. */
        fun buildOf(versionName: String?): Int? =
            versionName?.let { BUILD_IN_VERSION.find(it) }?.groupValues?.get(1)?.toIntOrNull()

        /**
         * GETs [url] through [calls]: the release's JSON, null on a non-200 answer. No token goes
         * with it: the release is public, and [calls] is a client of its own with none on it.
         * [budgetMillis] is for the whole call, so a server dripping its answer can't hold it.
         */
        suspend fun fetchRelease(calls: Call.Factory, url: HttpUrl = RELEASE_API, budgetMillis: Long = 30_000): String? =
            withContext(Dispatchers.IO) {
                val call = calls.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build())
                call.timeout().timeout(budgetMillis, TimeUnit.MILLISECONDS)
                call.execute().use { response ->
                    if (response.code != 200) return@use null
                    val source = response.body.source()
                    if (source.request(MAX_BODY + 1)) return@use null
                    source.buffer.readUtf8()
                }
            }
    }
}
