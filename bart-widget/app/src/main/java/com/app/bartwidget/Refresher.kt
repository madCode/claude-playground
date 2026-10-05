package com.app.bartwidget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object Refresher {
    private val lock = Mutex()

    /** Fetches every starred station; a station that fails keeps its last board, marked stale. */
    suspend fun refresh(context: Context) = lock.withLock {
        val starred = Store.starred(context).first()
        val old = Store.snapshot(context).first().boards
        val now = System.currentTimeMillis()
        val boards = coroutineScope {
            starred.map { abbr ->
                async {
                    runCatching { Board(abbr, now, BartApi.departures(abbr, now)) }.getOrElse {
                        old[abbr]?.copy(error = "Couldn't refresh") ?: Board(abbr, 0, emptyList(), "Couldn't refresh")
                    }
                }
            }.map { it.await() }
        }
        Store.saveSnapshot(context, Snapshot(boards.associateBy { it.abbr }))
        BartWidget().updateAll(context)
    }

    /** Every 15 minutes, Android's floor for background work; tap Refresh for a fresh board. */
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("refresh")
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Refresher.refresh(applicationContext)
        return Result.success()
    }
}
