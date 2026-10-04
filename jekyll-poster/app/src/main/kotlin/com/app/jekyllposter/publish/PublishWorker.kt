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

/** Publishes one queued post once there's a connection, retrying while GitHub can't be reached. */
class PublishWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosterApp).container
        val id = inputData.getLong(KEY_ID, -1)
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

        fun enqueue(context: Context, id: Long) {
            val request = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // One publish per post at a time: a second tap on Publish doesn't start a second commit.
            WorkManager.getInstance(context).enqueueUniqueWork("publish-$id", ExistingWorkPolicy.KEEP, request)
        }
    }
}
