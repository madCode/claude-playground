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
import kotlin.time.Duration.Companion.minutes

/** Publishes one queued post once there's a connection, retrying while GitHub can't be reached. */
class PublishWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosterApp).container
        val id = inputData.getLong(KEY_ID, -1)
        // Asked to go only through a VPN and there's none: wait a little for it here, then leave
        // it to WorkManager's backoff, and to the app, which starts the post again when the VPN
        // comes back while it runs. Waiting longer would spend the app's background time.
        if (!container.awaitVpn(2.minutes)) return Result.retry()
        return when (container.publisher.publish(id)) {
            Publisher.Outcome.Done -> {
                BuildWatchWorker.enqueue(applicationContext, id)
                Result.success()
            }
            Publisher.Outcome.Retry -> Result.retry()
            is Publisher.Outcome.Failed -> Result.failure()
        }
    }

    companion object {
        private const val KEY_ID = "draft"

        /**
         * Publishes post [id], not before [sendAfter] (epoch millis) when it has one. Replacing
         * any work left from before: it's only called for a post that isn't queued yet, so none
         * of it is running, and a stale one kept would ignore this [sendAfter].
         */
        fun enqueue(context: Context, id: Long, sendAfter: Long?) = enqueue(context, id, sendAfter, "publish-$id", ExistingWorkPolicy.REPLACE)

        /**
         * Starts a queued post's publish now, instead of when WorkManager's backoff would: a
         * second worker beside the waiting one, which it never stops (that could be mid-commit).
         * The publisher runs one publish at a time, and the later one finds the post sent.
         */
        fun retryNow(context: Context, id: Long, sendAfter: Long?) = enqueue(context, id, sendAfter, "publish-now-$id", ExistingWorkPolicy.KEEP)

        /** The writer's Send now: a worker of its own, which no delayed one can hold back. */
        fun sendNow(context: Context, id: Long) = enqueue(context, id, null, "send-now-$id", ExistingWorkPolicy.KEEP)

        private fun enqueue(context: Context, id: Long, sendAfter: Long?, name: String, policy: ExistingWorkPolicy) {
            val delay = ((sendAfter ?: 0) - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // The publisher runs one publish at a time; a second worker for the same post finds it sent.
            WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
        }
    }
}
