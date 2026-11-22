package spotter.app.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What CI publishes beside each debug build, as `spotter-debug.json` on the `spotter-debug`
 * release: the build's number, its commit, where its APK is, and the APK's exact size and
 * SHA-256, so a cut-off or swapped download is never handed to the installer.
 */
@Serializable
data class UpdateFeed(val build: Int, val commit: String, val apk: String, val size: Long, val sha256: String) {
    companion object {
        const val URL = "https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug.json"

        // A debug build is tens of megabytes; this is room to grow, not a size anyone expects.
        const val MAX_APK_BYTES = 200L * 1024 * 1024

        private val json = Json { ignoreUnknownKeys = true }
        private val hex = Regex("[0-9a-f]{64}")

        /**
         * The feed in [text], or null when it isn't one this app can trust: not JSON, a field
         * missing, or an APK that isn't on this repository's releases.
         */
        fun parse(text: String): UpdateFeed? {
            val feed = runCatching { json.decodeFromString<UpdateFeed>(text) }.getOrNull() ?: return null
            val ok = feed.build > 0 &&
                feed.size in 1..MAX_APK_BYTES &&
                hex.matches(feed.sha256) &&
                feed.apk.startsWith("https://github.com/madCode/claude-playground/releases/download/spotter-debug/")
            return feed.takeIf { ok }
        }
    }
}

/** Which build to install, if any. Pure, so the decision is tested without a phone. */
object UpdatePolicy {
    /** Attempts at one build before it is left alone; a newer build starts again. */
    const val MAX_ATTEMPTS = 3

    /**
     * The feed's build when it is newer than [installed] and hasn't failed [MAX_ATTEMPTS] times.
     * [installed] is 0 for a build CI didn't make, which never updates itself.
     */
    fun toInstall(installed: Int, feed: UpdateFeed?, failures: (Int) -> Int): UpdateFeed? =
        feed?.takeIf { installed > 0 && it.build > installed && failures(it.build) < MAX_ATTEMPTS }
}
