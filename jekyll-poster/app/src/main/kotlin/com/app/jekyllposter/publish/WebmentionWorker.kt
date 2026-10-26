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

class WebmentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PosterApp).container
        // Each site's tries are counted with the post, so retrying here ends once none waits.
        return if (container.webmentions.send(inputData.getLong(KEY_ID, -1))) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_ID = "draft"

        fun enqueue(context: Context, id: Long) {
            val request = OneTimeWorkRequestBuilder<WebmentionWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                // Pages' cache serves the page as it was for up to ten minutes after a deploy, and a
                // receiver that checks at once would find an Update's new link missing.
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("webmentions-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
