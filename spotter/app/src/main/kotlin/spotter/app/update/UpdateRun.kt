package spotter.app.update

import java.io.File
import java.io.IOException

/**
 * One update check, from the feed to handing a build to the installer, with Android's parts
 * passed in so every path runs in a JVM test. [UpdateWorker] wires the real ones.
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
            if (store.waiting <= installed) store.waiting = 0
            return Outcome.UP_TO_DATE
        }
        // Installing replaces the running app: never while it's on screen unless asked, and not
        // before Android lets Spotter install apps, which only a tap in Settings can grant.
        if (!tapped && (onScreen() || !canInstall())) {
            store.waiting = next.build
            return Outcome.LEFT_WAITING
        }
        val apk = apkFor(next.build)
        return try {
            download(next, apk)
            install(apk, next.build, !tapped)
            Outcome.HANDED_TO_INSTALLER
        } catch (e: Exception) {
            store.failed(next.build)
            apk.delete()
            if (e is IOException) Outcome.OFFLINE else Outcome.FAILED
        }
    }
}
