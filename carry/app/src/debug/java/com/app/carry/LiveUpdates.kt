package com.app.carry

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The CI debug build installing a newer build of itself when the owner taps the notice: download
 * it, check it is this app, signed with this app's key and newer, and hand it to Android.
 *
 * A build that fails those checks, or fails to install [MAX_INSTALL_FAILURES] times, is given up
 * on ([REJECTED], kept in [prefs] so a restart remembers): the notice then opens the download in
 * a browser instead, rather than fetching a file that won't install again and again.
 */
internal class LiveUpdates(
    private val context: Context,
    private val installedBuild: Int,
    private val prefs: SharedPreferences = prefsOf(context),
    private val dir: File = File(context.cacheDir, "updates"),
    private val download: (File) -> Unit = { UpdateInstaller.download(DebugUpdates.DOWNLOAD, it) },
    private val inspect: (File) -> ApkFacts? = { context.packageManager.apkFacts(it) },
    private val installed: () -> ApkFacts = { context.packageManager.installedFacts(context.packageName) },
    private val stage: (File) -> Int = { UpdateInstaller.stage(context, it) },
    private val commit: (Int, Int) -> Unit = { id, build -> UpdateInstaller.commit(context, id, build) },
    private val abandon: (Int) -> Unit = { UpdateInstaller.abandon(context, it) },
    private val canInstallNow: () -> Boolean = { UpdateInstaller.canInstall(context) },
    private val now: () -> Long = System::currentTimeMillis,
) : SelfUpdater {
    override val installing: StateFlow<Boolean> = UpdateInstaller.installing

    override fun canInstall() = canInstallNow()

    override fun next(build: Int, unsaved: Boolean, canInstall: Boolean): SelfUpdater.Step = when {
        build <= prefs.getInt(REJECTED, 0) -> SelfUpdater.Step.Browser
        unsaved -> SelfUpdater.Step.SaveFirst
        !canInstall -> SelfUpdater.Step.AllowInstalls
        else -> SelfUpdater.Step.Install
    }

    override suspend fun install(build: Int, unsaved: () -> Boolean): UpdateProblem? {
        if (build <= installedBuild || !UpdateInstaller.start()) return null
        var handedOver = false
        // Failures count against the build in the file once it passed its checks: the fixed-name
        // download can lag behind the newest build, which mustn't be given up on untried.
        var counted = build
        val problem = try {
            val apk = withContext(Dispatchers.IO) {
                dir.mkdirs()
                File(dir, "carry-debug.apk").apply { delete(); download(this) }
            }
            val facts = withContext(Dispatchers.IO) { inspect(apk) }
            when (val verdict = withContext(Dispatchers.IO) { verdict(facts, installed()) }) {
                is Verdict.Bad -> {
                    log("Build $build was refused: ${verdict.why}")
                    // The build asked for, not the one the file claims: a file that failed its
                    // checks can't be trusted to say which build it is.
                    reject(build)
                    UpdateProblem("The download isn't a Carry this phone can trust: ${verdict.why}.", offerLink = true)
                }
                is Verdict.Stale ->
                    UpdateProblem("The download isn't the newer build yet. Try again in a few minutes.", offerLink = false)
                Verdict.Good -> if (facts!!.versionCode <= prefs.getInt(REJECTED, 0)) {
                    // The newest build's file isn't up yet, and the one there was given up on.
                    UpdateProblem("Carry gave up on build ${facts.versionCode}; the newer one isn't up yet.", offerLink = true)
                } else {
                    counted = facts.versionCode.toInt()
                    val id = try {
                        withContext(Dispatchers.IO) { stage(apk) }
                    } catch (e: IOException) {
                        throw InstallFailed(e)
                    }
                    // Back on the caller's thread, after the slow parts and immediately before
                    // the commit, so nothing typed during them can be installed over.
                    if (unsaved()) {
                        abandon(id)
                        UpdateProblem("Save the After tab first, then tap the notice again. Updating restarts Carry.", offerLink = false)
                    } else {
                        commit(id, counted)
                        handedOver = true
                        null
                    }
                }
            }
        } catch (e: CancellationException) {
            // Once committed, Android's answer still comes; only an attempt that never got there
            // is over.
            if (!handedOver) UpdateInstaller.finish()
            throw e
        } catch (e: InstallFailed) {
            failure(counted, "Android couldn't take the update: ${e.cause?.message ?: "no room?"}.")
        } catch (e: IOException) {
            UpdateProblem("Couldn't download the update: ${e.message ?: "no connection"}.", offerLink = true)
        } catch (e: Exception) {
            // Anything Android's installer or an archive parser throws: said, never a crash,
            // because a crash takes unsaved typing with it.
            failure(counted, "Android wouldn't take the update: ${e.message ?: e.javaClass.simpleName}.")
        }
        if (problem != null) UpdateInstaller.finish()
        return problem
    }

    /** A failed hand-off to Android: counted, and said with the link once it's given up on. */
    private fun failure(build: Int, why: String): UpdateProblem =
        if (failed(prefs, build)) UpdateProblem("$why Carry won't try this build again.", offerLink = true)
        else UpdateProblem(why, offerLink = true)

    override fun resumed(activity: Activity, unsaved: Boolean) {
        UpdateInstaller.inFront = true
        runCatching { UpdateInstaller.forgetIfGone(activity) }
        // Android's question, which arrived while Carry was behind another app: shown once. Typed
        // since the tap, it's dropped rather than asked over the typing.
        UpdateInstaller.awaitingConfirm?.let { confirm ->
            UpdateInstaller.awaitingConfirm = null
            if (unsaved) {
                UpdateInstaller.committed?.let { abandon(it) }
                UpdateInstaller.finish()
            } else {
                runCatching { activity.startActivity(confirm) }.onFailure { UpdateInstaller.finish() }
            }
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
    }

    private fun reject(build: Int) = reject(prefs, build)

    private class InstallFailed(cause: Throwable) : Exception(cause)

    companion object {
        /** Each of these is one try; Android then says "can't install" for the same reasons. */
        const val MAX_INSTALL_FAILURES = 3
        /** An approved install is over in well under this; one still unanswered was left. */
        val UNANSWERED_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(5)
        internal const val REJECTED = "rejected"
        private const val FAILING = "failing"
        private const val FAILURES = "failures"

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

        private fun log(message: String) = Log.w("Carry", "Update: $message")
    }
}
