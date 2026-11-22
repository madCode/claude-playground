package spotter.app.export

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import spotter.app.log.EventLog
import spotter.app.log.LogDatabase
import java.util.concurrent.TimeUnit

/** Copies the log to the picked folder: after each save, once a day, and when a folder is picked. */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // The daily run and one after a save can fall together: one at a time, so neither writes a
    // file the other is writing, or forgets what the other wrote.
    override suspend fun doWork(): Result = oneAtATime.withLock { export() }

    private suspend fun export(): Result = withContext(Dispatchers.IO) {
        val store = ExportStore(applicationContext)
        val folder = store.folder ?: return@withContext Result.success()
        val (entries, unreadable) = EventLog(LogDatabase.get(applicationContext)).read()
        // A folder since deleted, or a grant taken back, fails here; the screen then says so.
        val ok = runCatching { ExportRun(store.written(folder), FolderTarget(applicationContext, Uri.parse(folder))).run(entries, unreadable) }.getOrDefault(false)
        store.finished(folder, System.currentTimeMillis(), ok)
        Result.success()
    }

    companion object {
        private val oneAtATime = Mutex()

        /** Once a day, so a save whose export failed (the folder's app asleep) is caught up. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "export-daily",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExportWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        // One chain, so two runs never write the same file at once; a run asked for while one is
        // under way follows it, since the log may have changed after that one read it.
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "export-run",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ExportWorker>().build(),
            )
        }
    }
}
