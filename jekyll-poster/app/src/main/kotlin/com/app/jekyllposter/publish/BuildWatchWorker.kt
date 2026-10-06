package com.app.jekyllposter.publish

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.app.jekyllposter.PosterApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class BuildWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosterApp).container
        // No giving up while the writer's VPN is off: the answer is only waiting for it.
        val giveUp = runAttemptCount >= MAX_ATTEMPTS && !container.waitingForVpn.first()
        val done = container.buildWatcher.check(inputData.getLong(KEY_ID, -1), giveUp = giveUp)
        return if (done) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_ID = "draft"

        /** Checks 30 s, 60 s, … apart: about eleven minutes in all; a Pages build usually takes one or two. */
        private const val MAX_ATTEMPTS = 6

        fun enqueue(context: Context, id: Long) {
            val request = OneTimeWorkRequestBuilder<BuildWatchWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("build-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
