package com.app.jekyllposter.update

import android.content.Context
import androidx.work.BackoffPolicy
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
import com.app.jekyllposter.PosterApp
import java.util.concurrent.TimeUnit

/**
 * Asks GitHub for a newer build every six hours, so builds arrive without the app being opened.
 * [DebugUpdates]' own three-hour limit still applies, shared with the check on opening the app.
 */
class LiveCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = (applicationContext as? PosterApp)?.container?.updates ?: return Result.success()
        updates.check.checkIfDue()
        updates.check.newer.value?.let(updates.updater::newerBuild)
        return Result.success()
    }

    companion object {
        const val UNIQUE = "live-update-check"

        fun schedule(work: WorkManager) {
            val request = PeriodicWorkRequestBuilder<LiveCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Downloads the newest build on an unmetered network, so a phone's data plan isn't spent on it. */
class LiveDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = LiveUpdates.of(applicationContext) ?: return Result.success()
        return when (updates.fetch(inputData.getInt(BUILD, 0))) {
            LiveUpdates.Outcome.Retry -> Result.retry()
            LiveUpdates.Outcome.Failed -> Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE = "live-update-download"
        private const val BUILD = "build"

        fun enqueue(work: WorkManager, build: Int) {
            val request = OneTimeWorkRequestBuilder<LiveDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
                .setInputData(workDataOf(BUILD to build))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            work.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/**
 * Installs the downloaded build once the app is closed, no post is going out and no editor or
 * Connect screen was left open. While either
 * holds, the install is tried again later, [RETRY_MINUTES] further apart each time.
 */
class LiveInstallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updates = LiveUpdates.of(applicationContext) ?: return Result.success()
        return when (updates.installInBackground()) {
            LiveUpdates.Outcome.Busy, LiveUpdates.Outcome.Retry -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val UNIQUE = "live-update-install"
        const val RETRY_MINUTES = 30L

        fun enqueue(work: WorkManager) {
            work.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request().build())
        }

        /**
         * After Android said there's no room. Delayed, since "storage not low" can hold while
         * the installer still refuses, and appended, since the attempt that got that answer
         * may not have finished yet.
         */
        fun enqueueAfterNoRoom(work: WorkManager) {
            val request = request().setInitialDelay(RETRY_MINUTES, TimeUnit.MINUTES).build()
            work.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        private fun request() = OneTimeWorkRequestBuilder<LiveInstallWorker>()
            // Android's copy of the build needs room as the download did.
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_MINUTES, TimeUnit.MINUTES)
    }
}
