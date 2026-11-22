package spotter.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Fetching the feed and the build it names, with nothing half-written left behind. */
object Download {
    /** The feed's text, or null when the server answered anything but 200. Throws offline. */
    fun text(url: String, maxBytes: Int = 64 * 1024): String? {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    out.write(chunk, 0, n)
                    if (out.size() > maxBytes) throw IOException("the feed is larger than any feed")
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads [feed]'s APK to [dest]. Throws [IOException] on a non-200 answer or when the
     * bytes aren't exactly the feed's size and SHA-256; [dest] then doesn't exist.
     */
    fun apk(feed: UpdateFeed, dest: File, url: String = feed.apk) {
        val partial = File(dest.parentFile, dest.name + ".part")
        dest.delete()
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("the download answered $code")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            partial.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > feed.size) throw IOException("the download is larger than the feed says")
                        digest.update(chunk, 0, n)
                        out.write(chunk, 0, n)
                    }
                }
            }
            if (total != feed.size) throw IOException("the download stopped at $total of ${feed.size} bytes")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != feed.sha256) throw IOException("the download isn't the build the feed names")
            if (!partial.renameTo(dest)) throw IOException("could not keep the download")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    // GitHub answers a release download with a redirect to its file host, which this follows.
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Spotter")
        }
}
