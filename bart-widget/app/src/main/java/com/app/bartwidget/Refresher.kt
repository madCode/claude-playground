package com.app.bartwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class Refresher(
    private val store: Store,
    private val api: BartApi,
    private val clock: () -> Long,
    private val updateWidgets: suspend () -> Unit,
) {
    private val lock = Mutex()

    /**
     * Fetches every starred station; a station that fails keeps its last board, marked with why.
     * Returns whether every station was fetched.
     */
    suspend fun refresh(): Boolean = lock.withLock {
        val starred = store.starred.first()
        val old = store.snapshot.first().boards
        val now = clock()
        val boards = coroutineScope {
            starred.map { abbr ->
                async {
                    runCatching { Board(abbr, now, api.departures(abbr, now)) }.getOrElse { e ->
                        val why = whyFailed(e)
                        old[abbr]?.copy(error = why) ?: Board(abbr, 0, emptyList(), why)
                    }
                }
            }.awaitAll()
        }
        store.saveSnapshot(Snapshot(boards.associateBy { it.abbr }))
        updateWidgets()
        boards.none { it.error != null }
    }

    companion object {
        private const val WORK = "refresh"

        /** Every 15 minutes, Android's floor for background work; tap Refresh for a fresh board. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // A failed run tries again within a few minutes rather than waiting out the 15. Capped,
    // because WorkManager's backoff doubles each time: after an hour offline, the next try
    // would be hours away.
    override suspend fun doWork(): Result = when {
        applicationContext.container.refresher.refresh() -> Result.success()
        runAttemptCount < MAX_RETRIES -> Result.retry()
        else -> Result.success()
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}
