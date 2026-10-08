package com.app.bartwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The widget's Refresh, run as a foreground service. A widget tap reaches the app in the
 * background, where Data Saver, battery restrictions and power saving can cut off its network;
 * a foreground service keeps network access. Android lets an app start one from a widget tap.
 * Android holds back the notification for the first few seconds, so a normal refresh shows none.
 */
class RefreshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        scope.launch {
            try {
                container.refresher.refresh()
            } finally {
                // Stops only once the latest tap's refresh is done, not an earlier one's.
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.refreshing))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
        .also {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.refreshing_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }

    companion object {
        private const val CHANNEL = "refresh"
        private const val NOTIFICATION_ID = 1

        /** Refreshes from a widget tap; if Android won't start the service, refreshes here instead. */
        suspend fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RefreshService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException, an IllegalStateException, on Android 12+.
                context.container.refresher.refresh()
            }
        }
    }
}
