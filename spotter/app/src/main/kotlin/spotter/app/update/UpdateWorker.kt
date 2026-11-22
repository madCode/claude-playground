package spotter.app.update

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import spotter.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks the debug release for a newer CI build and installs it. In the background it installs
 * only while Spotter isn't on screen; otherwise it leaves the build waiting for a tap.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val run = UpdateRun(
            installed = BuildConfig.CI_BUILD,
            store = UpdateStore(context),
            fetchFeed = { Download.text(UpdateFeed.URL) },
            download = { feed, apk -> Download.apk(feed, apk) },
            install = { apk, build, background -> Installer.install(context, apk, build, background) },
            apkFor = { build -> File(context.cacheDir, "updates/spotter-$build.apk").apply { parentFile?.mkdirs() } },
            onScreen = { runBlocking(Dispatchers.Main) { onScreen() } },
            canInstall = { Installer.canInstall(context) },
        )
        when (run.run(tapped = inputData.getBoolean(TAPPED, false))) {
            UpdateRun.Outcome.OFFLINE -> Result.retry()
            UpdateRun.Outcome.FAILED -> Result.failure()
            else -> Result.success()
        }
    }

    private fun onScreen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        private const val TAPPED = "tapped"

        private const val RUN = "update-run"

        /** Every three hours on any network, and once now. Does nothing on a build CI didn't make. */
        fun schedule(context: Context) {
            if (BuildConfig.CI_BUILD == 0) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "update-check",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Tick>(3, TimeUnit.HOURS).setConstraints(network).build(),
            )
            runOnce(context, tapped = false)
        }

        /** The person tapped "Install": download and install, confirmation and all. */
        fun installNow(context: Context) = runOnce(context, tapped = true)

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        // Every run goes through one unique chain, so two never download into the same file or
        // race to the installer. A tap waits for a run under way rather than cancelling it: a
        // blocking download can't be interrupted, so a cancelled one would keep writing.
        private fun runOnce(context: Context, tapped: Boolean) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>().setInputData(workDataOf(TAPPED to tapped))
            if (!tapped) request.setConstraints(network)
            WorkManager.getInstance(context).enqueueUniqueWork(
                RUN,
                if (tapped) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request.build(),
            )
        }
    }

    /** The periodic check only queues a run on the one chain, so it never overlaps another. */
    class Tick(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            runOnce(applicationContext, tapped = false)
            return Result.success()
        }
    }
}
