package spotter.app.update

import java.io.File
import java.io.IOException

/**
 * One update check, from the feed to handing a build to the installer, with Android's parts
 * passed in so every path runs in a JVM test. [UpdateWorker] wires the real ones; WorkManager
 * runs one at a time.
 */
class UpdateRun(
    private val installed: Int,
    private val store: Store,
    private val fetchFeed: () -> String?,
    private val download: (UpdateFeed, File) -> Unit,
    private val install: (File, Int, Boolean) -> Unit,
    private val apkFor: (Int) -> File,
    private val onScreen: () -> Boolean,
    private val canInstall: () -> Boolean,
) {
    /** The parts of [UpdateStore] a run uses. */
    interface Store {
        var waiting: Int
        /** A build Android asked to confirm: downloading it again in the background is wasted. */
        var needsTap: Int
        fun failures(build: Int): Int
        fun failed(build: Int)
    }

    enum class Outcome { OFFLINE, UP_TO_DATE, LEFT_WAITING, HANDED_TO_INSTALLER, FAILED }

    /** [tapped]: the person asked for this build now, so a confirmation dialog is welcome. */
    fun run(tapped: Boolean): Outcome {
        val feed = try {
            fetchFeed()?.let(UpdateFeed::parse)
        } catch (_: IOException) {
            return Outcome.OFFLINE
        }
        val next = UpdatePolicy.toInstall(installed, feed, store::failures)
        if (next == null) {
            // Up to date, or the newer build was given up on: either way nothing is on offer.
            store.waiting = 0
            store.needsTap = 0
            return Outcome.UP_TO_DATE
        }
        // Installing replaces the running app: never while it's on screen unless asked, and not
        // before Android lets Spotter install apps, which only a tap in Settings can grant. A
        // build Android already asked to confirm waits for the tap rather than downloading
        // again every few hours, on mobile data too.
        if (!tapped && (onScreen() || !canInstall() || store.needsTap == next.build)) {
            store.waiting = next.build
            return Outcome.LEFT_WAITING
        }
        val apk = apkFor(next.build)
        try {
            download(next, apk)
            // The download takes a while; Spotter may have been opened meanwhile.
            if (!tapped && onScreen()) {
                store.waiting = next.build
                return Outcome.LEFT_WAITING
            }
            install(apk, next.build, !tapped)
            return Outcome.HANDED_TO_INSTALLER
        } catch (e: BadDownload) {
            store.failed(next.build)
            return Outcome.FAILED
        } catch (_: IOException) {
            // A dropped line or a timeout says nothing against the build: try again later.
            return Outcome.OFFLINE
        } catch (_: Exception) {
            store.failed(next.build)
            return Outcome.FAILED
        } finally {
            // The installer session holds its own copy.
            apk.delete()
        }
    }
}
