package com.app.carry

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The Android half of noticing a new session file: a periodic check, a
 * notification, and the record of what has been seen.
 *
 * All the decisions live in [SessionWatcher] and [NewSession], which know
 * nothing about Android. This is the shell that supplies them with a
 * folder, a memory, a date and a way to speak — deliberately thin, because
 * nothing in here is reachable from a plain unit test.
 */
class SessionWatchWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /**
     * A CoroutineWorker with a timeout, not a plain Worker, for the reason
     * `CarryViewModel.refresh` already gives about the identical call: a
     * document provider can block for ever, `openInputStream` has no
     * timeout and `runCatching` never fires. On the UI thread that strands
     * the owner on "Reading"; here it would hold one of WorkManager's few
     * executor threads and a WAKE_LOCK until the 10-minute cap, every hour,
     * on the phone this feature was meant to be gentle with.
     *
     * The timeout cannot interrupt a stuck binder call -- nothing can --
     * but it frees the worker to finish instead of waiting on it.
     */
    override suspend fun doWork(): Result {
        // Never Result.retry(): there is nothing here worth retrying. The
        // next period comes round soon enough, and a retry storm against a
        // provider that is simply unavailable costs battery for nothing.
        withTimeoutOrNull(CarryViewModel.STALL_AFTER_MS) {
            runCatching {
                SessionWatcher.watchOnce(
                    folder = SessionFolder(context),
                    seen = PrefsSeen(context),
                    today = LocalDate.now(),
                    post = { found -> notify(context, found) },
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK = "session-watch"
        private const val CHANNEL = "sheets"
        private const val ID = 1

        /**
         * One hour, not the fifteen-minute floor. The session file lands
         * hours before the class, so the extra precision buys nothing and
         * the owner's laptop is already supplying a 16-inch MacBook Pro
         * from a 30W adapter — being cheap with their phone's battery is
         * the same courtesy. Doze will stretch this further and that is
         * fine.
         *
         * KEEP rather than UPDATE: re-enqueueing on every app open must
         * not restart the period, or opening the app often would mean the
         * check never actually runs.
         */
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<SessionWatchWorker>(1, TimeUnit.HOURS).build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, work)
            }
        }

        /**
         * Whether the owner has already been asked for the permission.
         *
         * Kept so the dialog appears once rather than on every resume.
         * Android stops showing it after a refusal anyway, but "the system
         * will ignore us" is not a reason to keep asking.
         */
        fun asked(context: Context): Boolean =
            context.getSharedPreferences("carry", Context.MODE_PRIVATE)
                .getBoolean("asked_notify", false)

        fun markAsked(context: Context) {
            context.getSharedPreferences("carry", Context.MODE_PRIVATE)
                .edit().putBoolean("asked_notify", true).apply()
        }

        /** Whether a notification would actually be delivered if posted. */
        fun canNotify(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        private fun notify(context: Context, found: List<NewSession.Found>) {
            val text = NewSession.text(found) ?: return
            if (!canNotify(context)) return

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "New sheets", NotificationManager.IMPORTANCE_DEFAULT)
            )

            // SINGLE_TOP, and emphatically NOT CLEAR_TOP. With a standard
            // launch mode, CLEAR_TOP FINISHES the running activity and
            // builds a new one, which clears the ViewModelStore --
            // CarryViewModel's edits, added rows and in-flight save with
            // it. The owner types the After tab's weights, locks the phone,
            // the second sheet of a borrowed day lands, they tap the
            // notification to look: numbers gone. For this app that is the
            // cardinal failure, and a tap arriving mid-save could cancel
            // the coroutine between the write and the verify-and-restore
            // that SessionFolder.save exists for.
            //
            // The cost is that the app comes back on whatever tab they
            // left, not necessarily Before. That is the right trade: `tab`
            // is rememberSaveable because where you were is worth keeping,
            // and losing typed numbers to land on a particular tab is not a
            // bargain.
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val note = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Carry")
                // `text` is the only thing that decides what reaches a
                // lock screen. It carries the CLASS and nothing else —
                // less than the design's "class and date", because the
                // check only ever fires for today's file, so a date would
                // be noise on a notification that means "now".
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()

            // Checked again, inline, immediately before the post. Not
            // belt-and-braces: lint cannot see through `canNotify` to the
            // permission test, and it is right to insist — the grant can
            // be revoked between that call and this one, and a background
            // worker that throws is worse than a missed notification.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            runCatching { NotificationManagerCompat.from(context).notify(ID, note) }
        }
    }
}

/** [Seen], kept in the same prefs file the folder grant already uses. */
class PrefsSeen(context: Context) : Seen {
    private val prefs = context.getSharedPreferences("carry", Context.MODE_PRIVATE)

    override fun names(): Set<String>? = prefs.getStringSet(KEY, null)

    override fun remember(names: Set<String>) {
        prefs.edit().putStringSet(KEY, names).apply()
    }

    private companion object {
        const val KEY = "seen_sessions"
    }
}
