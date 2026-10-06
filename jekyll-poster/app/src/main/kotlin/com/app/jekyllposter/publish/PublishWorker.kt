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

        fun enqueue(context: Context, id: Long) = enqueue(context, id, "publish-$id")

        /**
         * Starts a queued post's publish now, instead of when WorkManager's backoff would: a
         * second worker beside the waiting one, which it never stops (that could be mid-commit).
         * The publisher runs one publish at a time, and the later one finds the post sent.
         */
        fun retryNow(context: Context, id: Long) = enqueue(context, id, "publish-now-$id")

        private fun enqueue(context: Context, id: Long, name: String) {
            val request = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // One publish per post at a time: a second tap on Publish doesn't start a second commit.
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        }
    }
}
