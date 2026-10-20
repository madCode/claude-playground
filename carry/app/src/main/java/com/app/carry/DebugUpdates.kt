package com.app.carry

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Notices when CI has published a newer debug build than the one installed, so the owner isn't
 * left on a stale one. Asks GitHub when the app comes to the front, at most every
 * [INTERVAL_MILLIS]. This is the app's only network use: the owner chose it on 2026-10-16
 * (claude-playground #107) over routing the number through the synced folder.
 *
 * @param installed this app's CI build number, or null for a release or locally built app, which
 *   never asks.
 * @param fetch the release's JSON, or null when GitHub answered with anything but 200; throws
 *   [IOException] when there was no answer at all.
 */
class DebugUpdates(
    private val prefs: SharedPreferences,
    private val installed: Int?,
    private val fetch: suspend () -> String? = { askGitHub() },
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
            // Not counted: offline, it asks again next time the app comes to the front.
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

        /** Shared by the check on opening Carry and the debug build's background check. */
        fun prefsOf(context: android.content.Context): SharedPreferences =
            context.getSharedPreferences("debug-updates", android.content.Context.MODE_PRIVATE)
        const val RELEASE_API = "https://api.github.com/repos/madCode/claude-playground/releases/tags/carry-debug"
        const val DOWNLOAD = "https://github.com/madCode/claude-playground/releases/download/carry-debug/carry-debug.apk"
        private const val CHECKED = "checked"
        private const val LATEST = "latest"

        // A release answer is a few kilobytes; anything far larger is not one, and is not read.
        private const val MAX_BODY = 256 * 1024

        // CI names each build's file after its run number, beside the fixed name DOWNLOAD links to.
        private val BUILD_FILE = Regex("""carry-debug-(\d{1,9})\.apk""")
        private val BUILD_IN_VERSION = Regex("""-debug\.(\d{1,9})""")

        /** The newest build number among the release's files, from GitHub's release JSON. */
        fun latestBuild(releaseJson: String): Int? =
            BUILD_FILE.findAll(releaseJson).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()

        /** The CI build number in a version name like "0.1.0-debug.131+012eb32"; null without one. */
        fun buildOf(versionName: String?): Int? =
            versionName?.let { BUILD_IN_VERSION.find(it) }?.groupValues?.get(1)?.toIntOrNull()

        /** GETs [url] on the calling thread: the release's JSON, null on a non-200 answer. */
        fun fetchRelease(url: String = RELEASE_API, budgetMillis: Long = 30_000, clock: () -> Long = System::currentTimeMillis): String? {
            val deadline = clock() + budgetMillis
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Carry")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                // Read by hand: InputStream.readNBytes is API 33, and this runs from 26.
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        out.write(chunk, 0, n)
                        if (out.size() > MAX_BODY) return null
                        // readTimeout is per read, so a server dripping a byte every few seconds
                        // would hold this check, and every later one, indefinitely without it.
                        if (clock() > deadline) throw IOException("release took over ${budgetMillis / 1000} s")
                    }
                }
                return out.toString(Charsets.UTF_8.name())
            } finally {
                connection.disconnect()
            }
        }

        private suspend fun askGitHub(): String? =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchRelease(RELEASE_API) }
    }
}
