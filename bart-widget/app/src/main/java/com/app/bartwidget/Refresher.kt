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

    /** Fetches every starred station; a station that fails keeps its last board, marked stale. */
    suspend fun refresh() = lock.withLock {
        val starred = store.starred.first()
        val old = store.snapshot.first().boards
        val now = clock()
        val boards = coroutineScope {
            starred.map { abbr ->
                async {
                    runCatching { Board(abbr, now, api.departures(abbr, now)) }.getOrElse {
                        old[abbr]?.copy(error = STALE) ?: Board(abbr, 0, emptyList(), STALE)
                    }
                }
            }.awaitAll()
        }
        store.saveSnapshot(Snapshot(boards.associateBy { it.abbr }))
        updateWidgets()
    }

    companion object {
        const val STALE = "Couldn't refresh"
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
    override suspend fun doWork(): Result {
        applicationContext.container.refresher.refresh()
        return Result.success()
    }
}
