package com.app.jekyllposter.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.work.WorkManager
import com.app.jekyllposter.PosterApp
import com.app.jekyllposter.core.github.NoVpnException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call

/**
 * The CI debug build keeping itself current. A newer build is downloaded on Wi-Fi in the
 * background, checked (this app, this app's key, newer), and installed once the app is closed
 * and no post is being sent. A tap on the notice installs it at once, downloading first if the
 * background hasn't.
 *
 * What it knows lives in [prefs], so a process that dies part way picks up where it was. A build
 * that fails the checks, can't be downloaded, or fails to install [MAX_INSTALL_FAILURES] times is
 * given up on ([REJECTED]): only a newer one is fetched again, and the notice's tap opens the
 * download in a browser instead.
 *
 * @param calls the update client, behind the VPN switch: see `AppContainer.updateCalls`.
 * @param waitFor what a background install waits for now, beside the app being on screen: a post
 *   being sent, or a screen open or left open when the app went away ([UpdateHolds]).
 */
internal class LiveUpdates(
    private val context: Context,
    private val installedBuild: Int,
    calls: Call.Factory,
    private val waitFor: () -> Hold?,
    private val prefs: SharedPreferences = prefsOf(context),
    private val dir: File = File(context.cacheDir, "updates"),
    private val download: (File) -> Unit = { UpdateInstaller.download(calls, DebugUpdates.DOWNLOAD, it) },
    private val inspect: (File) -> ApkFacts? = { context.packageManager.apkFacts(it) },
    private val installed: () -> ApkFacts = { context.packageManager.installedFacts(context.packageName) },
    private val stage: (File) -> Int = { UpdateInstaller.stage(context, it) },
    private val commit: (id: Int, build: Int, background: Boolean) -> Unit =
        { id, build, background -> UpdateInstaller.commit(context, id, build, background) },
    private val abandon: (Int) -> Unit = { UpdateInstaller.abandon(context, it) },
    private val canInstallNow: () -> Boolean = { UpdateInstaller.canInstall(context) },
    private val now: () -> Long = System::currentTimeMillis,
    /** Why a background install must wait: the app on screen, only just left, or [waitFor]. */
    private val busy: () -> Boolean = { mustWait(now(), waitFor() != null) },
    private val work: () -> WorkManager = { WorkManager.getInstance(context) },
) : SelfUpdater {
    override val installing: StateFlow<Boolean> = UpdateInstaller.installing

    override fun canInstall() = canInstallNow()

    override fun start() {
        // The update that brought this build in has done its job.
        if (prefs.getInt(READY, 0) in 1..installedBuild) clearUp()
        // A tap's download, kept only until it's handed over.
        else if (!UpdateInstaller.installing.value) File(dir, TAPPED).delete()
        LiveCheckWorker.schedule(work())
    }

    override fun newerBuild(build: Int) {
        if (build <= installedBuild || build <= prefs.getInt(REJECTED, 0)) return
        if (readyApk(build) != null) LiveInstallWorker.enqueue(work())
        // Kept, not replaced, if one is waiting for Wi-Fi: it fetches the newest build anyway.
        else LiveDownloadWorker.enqueue(work(), build)
    }

    override fun next(build: Int, publishing: Boolean, canInstall: Boolean): SelfUpdater.Step = when {
        build <= prefs.getInt(REJECTED, 0) -> SelfUpdater.Step.Browser
        publishing -> SelfUpdater.Step.WaitForPost
        !canInstall -> SelfUpdater.Step.AllowInstalls
        else -> SelfUpdater.Step.Install
    }

    override suspend fun install(build: Int, hold: () -> Hold?): UpdateProblem? {
        if (build <= installedBuild) return null
        // The background may be part way through installing it: said, rather than a tap that
        // does nothing.
        if (!UpdateInstaller.start()) return UpdateProblem("The app is already installing an update. Give it a minute.", offerLink = false)
        var handedOver = false
        // Failures count against the build in the file once it passed its checks: the fixed-name
        // download can lag behind the newest build, which mustn't be given up on untried.
        var counted = build
        val problem = try { files.withLock locked@{
            // Downloaded in the background already, or now.
            val apk = readyApk(build) ?: withContext(Dispatchers.IO) {
                dir.mkdirs()
                File(dir, TAPPED).apply { delete(); download(this) }
            }
            val facts = withContext(Dispatchers.IO) { inspect(apk) }
            when (val verdict = withContext(Dispatchers.IO) { verdict(facts, installed()) }) {
                is Verdict.Bad -> {
                    log("Build $build was refused: ${verdict.why}")
                    // The build asked for, not the one the file claims: a file that failed its
                    // checks can't be trusted to say which build it is.
                    reject(build)
                    UpdateProblem("The download isn't a Jekyll Poster this phone can trust: ${verdict.why}.", offerLink = true)
                }
                is Verdict.Stale ->
                    UpdateProblem("The download isn't the newer build yet. Try again in a few minutes.", offerLink = false)
                Verdict.Good -> if (facts!!.versionCode <= prefs.getInt(REJECTED, 0)) {
                    // The newest build's file isn't up yet, and the one there was given up on.
                    UpdateProblem("The app gave up on build ${facts.versionCode}; the newer one isn't up yet.", offerLink = true)
                } else {
                    counted = facts.versionCode.toInt()
                    val id = try {
                        withContext(Dispatchers.IO) { stage(apk) }
                    } catch (e: IOException) {
                        // Usually no room: the phone's, not the build's, so not counted against it.
                        return@locked UpdateProblem(
                            "Android couldn't take the update: ${e.message ?: "no room?"}. Free some space, then tap again.",
                            offerLink = false,
                        )
                    }
                    // After the slow parts and immediately before the commit, so a post that
                    // started going out, or one opened, during them isn't cut short.
                    when (hold()) {
                        Hold.Post -> {
                            abandon(id)
                            keepReady(apk, counted)
                            UpdateProblem(SelfUpdater.WAIT_FOR_SENT, offerLink = false, resumes = true)
                        }
                        Hold.Screen -> {
                            abandon(id)
                            keepReady(apk, counted)
                            UpdateProblem(SelfUpdater.WAIT_FOR_SCREEN, offerLink = false, resumes = true)
                        }
                        null -> {
                            commit(id, counted, false)
                            handedOver = true
                            null
                        }
                    }
                }
            }
        } } catch (e: CancellationException) {
            // Once committed, Android's answer still comes; only an attempt that never got there
            // is over.
            if (!handedOver) UpdateInstaller.finish()
            throw e
        } catch (_: NoVpnException) {
            // No link: the browser would fetch it around the VPN the writer asked for.
            UpdateProblem(SelfUpdater.WAIT_FOR_VPN, offerLink = false)
        } catch (e: IOException) {
            UpdateProblem("Couldn't download the update: ${e.message ?: "no connection"}.", offerLink = true)
        } catch (e: Exception) {
            // Anything Android's installer or an archive parser throws: said, never a crash.
            failure(counted, "Android wouldn't take the update: ${e.message ?: e.javaClass.simpleName}.")
        }
        if (problem != null) UpdateInstaller.finish()
        return problem
    }

    /** A failed hand-off to Android: counted, and said with the link once it's given up on. */
    private fun failure(build: Int, why: String): UpdateProblem =
        if (failed(prefs, build)) UpdateProblem("$why The app won't try this build again.", offerLink = true)
        else UpdateProblem(why, offerLink = true)

    override fun resumed(activity: Activity) {
        UpdateInstaller.inFront = true
        runCatching { UpdateInstaller.forgetIfGone(activity) }
        // Android's question, which arrived while the app was behind another: shown once.
        UpdateInstaller.awaitingConfirm?.let { confirm ->
            UpdateInstaller.awaitingConfirm = null
            runCatching { activity.startActivity(confirm) }.onFailure { UpdateInstaller.finish() }
            return
        }
        // Still no answer long after the hand-off: the question was left with Home, which sends
        // none. Asking again can't tell that from an approved install still finishing, so the
        // session is dropped instead and the notice can be tapped again.
        val id = UpdateInstaller.committed ?: return
        if (now() - UpdateInstaller.committedAt > UNANSWERED_MILLIS) {
            abandon(id)
            UpdateInstaller.finish()
        }
    }

    override fun paused() {
        UpdateInstaller.inFront = false
        UpdateInstaller.leftFrontAt = now()
    }

    /**
     * Fetches and checks the newest build, which [target] is or something newer. Every run counts
     * as a try, whether it fails, is stopped (Wi-Fi lost, WorkManager's 10-minute limit) or dies
     * with the process: after [DOWNLOAD_TRIES] the build is given up on.
     */
    suspend fun fetch(target: Int): Outcome = files.withLock {
        withContext(Dispatchers.IO) {
            if (target <= installedBuild || target <= prefs.getInt(REJECTED, 0)) return@withContext Outcome.Done
            if (readyApk(target) != null) {
                LiveInstallWorker.enqueue(work())
                return@withContext Outcome.Done
            }
            val tries = (if (prefs.getInt(DOWNLOADING, 0) == target) prefs.getInt(TRIES, 0) else 0) + 1
            prefs.edit().putInt(DOWNLOADING, target).putInt(TRIES, tries).commit()
            fun again() = if (tries < DOWNLOAD_TRIES) Outcome.Retry else Outcome.Failed.also {
                log("Build $target: given up after $DOWNLOAD_TRIES tries")
                reject(target)
            }
            if (tries > DOWNLOAD_TRIES) return@withContext again()
            dir.mkdirs()
            val part = File(dir, "download.part")
            try {
                download(part)
            } catch (e: IOException) {
                log("Build $target didn't download: ${e.message}")
                return@withContext again()
            }
            val facts = inspect(part)
            when (val verdict = verdict(facts, installed())) {
                is Verdict.Bad -> {
                    part.delete()
                    log("Build $target was refused: ${verdict.why}")
                    reject(target)
                    return@withContext Outcome.Failed
                }
                is Verdict.Stale -> {
                    part.delete()
                    return@withContext again()
                }
                // The newest build's file isn't up yet, and the one there was given up on.
                // Not counted as a try: the newer build hasn't been seen yet.
                Verdict.Good -> if (facts!!.versionCode <= prefs.getInt(REJECTED, 0)) {
                    part.delete()
                    prefs.edit().putInt(TRIES, tries - 1).commit()
                    return@withContext Outcome.Retry
                }
            }
            val build = facts!!.versionCode.toInt()
            val apk = apkOf(build)
            if (!part.renameTo(apk)) {
                part.delete()
                return@withContext again()
            }
            dir.listFiles()?.filter { it != apk }?.forEach { it.delete() }
            prefs.edit().putInt(READY, build).remove(ASKED).remove(DOWNLOADING).remove(TRIES).commit()
            LiveInstallWorker.enqueue(work())
            Outcome.Done
        }
    }

    /**
     * Installs the downloaded build while the app is closed and no post is going out. If Android
     * wants the writer to confirm, it waits for their tap on the notice instead ([needsConfirming]).
     */
    suspend fun installInBackground(): Outcome = files.withLock {
        withContext(Dispatchers.IO) {
            val ready = prefs.getInt(READY, 0)
            if (ready == 0) return@withContext Outcome.Done
            if (ready <= installedBuild) {
                clearUp()
                return@withContext Outcome.Done
            }
            val apk = apkOf(ready)
            if (!apk.isFile) {
                // Android clears caches when space runs low: fetched again on the next check.
                prefs.edit().remove(READY).remove(ASKED).commit()
                return@withContext Outcome.Done
            }
            if (ready <= prefs.getInt(REJECTED, 0) || prefs.getInt(ASKED, 0) == ready || !canInstallNow()) {
                return@withContext Outcome.Done
            }
            if (busy()) return@withContext Outcome.Busy
            // Checked again: a file in the cache could have changed since it was fetched.
            if (verdict(inspect(apk), installed()) != Verdict.Good) {
                reject(ready)
                clearUp()
                return@withContext Outcome.Done
            }
            // A tap's attempt is under way.
            if (!UpdateInstaller.start()) return@withContext Outcome.Busy
            var handedOver = false
            try {
                val id = stage(apk)
                // Asked again just before Android takes it: the copy takes a while, and the writer
                // may have opened the app, or a queued post started going out, meanwhile.
                if (busy()) {
                    abandon(id)
                    return@withContext Outcome.Busy
                }
                commit(id, ready, true)
                handedOver = true
                Outcome.Started
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Usually no room: tried again later, not counted against the build.
                log("Build $ready couldn't be staged: ${e.message}")
                Outcome.Retry
            } catch (e: Exception) {
                log("Build $ready couldn't be handed to Android: ${e.javaClass.simpleName} ${e.message}")
                if (failed(prefs, ready)) Outcome.Failed else Outcome.Retry
            } finally {
                if (!handedOver) UpdateInstaller.finish()
            }
        }
    }

    private fun readyApk(atLeast: Int): File? =
        prefs.getInt(READY, 0).takeIf { it >= atLeast && it > installedBuild }?.let(::apkOf)?.takeIf { it.isFile }

    private fun apkOf(build: Int) = File(dir, "build-$build.apk")

    /** A tapped download, checked, kept as the one to install once nothing holds it back. */
    private fun keepReady(apk: File, build: Int) {
        val ready = apkOf(build)
        if (apk != ready && !apk.renameTo(ready)) return
        prefs.edit().putInt(READY, build).remove(ASKED).commit()
        LiveInstallWorker.enqueue(work())
    }

    private fun clearUp() {
        dir.listFiles()?.forEach { it.delete() }
        prefs.edit().remove(READY).remove(ASKED).commit()
    }

    private fun reject(build: Int) = reject(prefs, build)

    /** How a background step went, for its worker. */
    enum class Outcome { Done, Started, Busy, Retry, Failed }

    companion object {
        /** Each of these is one try; Android then says "can't install" for the same reasons. */
        const val MAX_INSTALL_FAILURES = 3
        /** An approved install is over in well under this; one still unanswered was left. */
        val UNANSWERED_MILLIS = TimeUnit.MINUTES.toMillis(5)
        /** With the backoff, about a quarter of an hour of a bad connection. */
        const val DOWNLOAD_TRIES = 5
        /** The editor saves the last keystrokes 0.4 s after them, even with the app closed. */
        val SETTLE_MILLIS = TimeUnit.SECONDS.toMillis(10)
        internal const val REJECTED = "rejected"
        internal const val READY = "ready"
        private const val ASKED = "asked"
        private const val DOWNLOADING = "downloading"
        private const val TRIES = "tries"
        private const val FAILING = "failing"
        private const val FAILURES = "failures"
        private const val TAPPED = "jekyll-poster-debug.apk"

        /** Whether a background install must wait at [now]: see [busy]. */
        fun mustWait(now: Long, held: Boolean): Boolean =
            UpdateInstaller.inFront || now - UpdateInstaller.leftFrontAt in 0 until SETTLE_MILLIS || held

        /** One step at a time: a tap and a background run share the files in the cache. */
        private val files = Mutex()

        /** The running build's own updater, for its workers; null for a build made locally. */
        fun of(context: Context): LiveUpdates? =
            (context.applicationContext as? PosterApp)?.container?.updates?.updater as? LiveUpdates

        /** A background install of [build] needs the writer's yes: it waits for their tap. */
        fun needsConfirming(context: Context, build: Int) {
            if (build > 0) prefsOf(context).edit().putInt(ASKED, build).commit()
        }

        fun prefsOf(context: Context): SharedPreferences =
            context.getSharedPreferences("live-updates", Context.MODE_PRIVATE)

        /**
         * Android's answer for [build] was a failure: true once it's given up on. A file wrong for
         * this phone or this app fails the same way every time, so it's given up on at once.
         */
        fun installFailed(context: Context, build: Int, status: Int): Boolean {
            if (build <= 0) return false
            val prefs = prefsOf(context)
            log("Build $build didn't install (status $status)")
            return when (status) {
                PackageInstaller.STATUS_FAILURE_INVALID,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_CONFLICT -> { reject(prefs, build); true }
                // No room, or the storage it installs to is missing: the phone's, not the build's.
                // A downloaded build is tried again later.
                PackageInstaller.STATUS_FAILURE_STORAGE -> { LiveInstallWorker.enqueueAfterNoRoom(WorkManager.getInstance(context)); false }
                else -> failed(prefs, build)
            }
        }

        /** Counts a failed install of [build]; true once it's given up on. */
        private fun failed(prefs: SharedPreferences, build: Int): Boolean {
            val before = if (prefs.getInt(FAILING, 0) == build) prefs.getInt(FAILURES, 0) else 0
            prefs.edit().putInt(FAILING, build).putInt(FAILURES, before + 1).commit()
            if (before + 1 < MAX_INSTALL_FAILURES) return false
            reject(prefs, build)
            return true
        }

        private fun reject(prefs: SharedPreferences, build: Int) {
            if (build > prefs.getInt(REJECTED, 0)) prefs.edit().putInt(REJECTED, build).commit()
        }

        private fun log(message: String) = Log.w("JekyllPoster", "Update: $message")
    }
}
