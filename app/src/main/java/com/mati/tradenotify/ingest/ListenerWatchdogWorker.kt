package com.mati.tradenotify.ingest

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mati.tradenotify.alarm.AlarmNotifications
import com.mati.tradenotify.util.TAG
import java.util.concurrent.TimeUnit

/**
 * Periodically confirms the notification listener is still alive.
 *
 * A silently dead listener is this app's worst failure: everything looks fine and no alarm ever
 * comes. The watchdog re-binds it and, if the permission itself was revoked, says so out loud.
 */
class ListenerWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val component = ComponentName(context, TelegramNotificationListener::class.java)

        val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

        if (!granted) {
            Log.w(TAG, "Notification access is not granted; alarms cannot fire")
            AlarmNotifications.postStatus(
                context,
                "TradeNotify is not listening",
                "Notification access is off, so trading signals cannot be detected. " +
                    "Tap to re-enable it.",
                id = WATCHDOG_NOTIFICATION_ID,
            )
            return Result.success()
        }

        if (!TelegramNotificationListener.connected.value) {
            Log.w(TAG, "Listener granted but not connected; requesting rebind")
            runCatching { NotificationListenerService.requestRebind(component) }
                .onFailure { Log.e(TAG, "requestRebind failed: ${it.message}") }
        }

        // Clear a previous warning once things are healthy again.
        NotificationManagerCompat.from(context).cancel(WATCHDOG_NOTIFICATION_ID)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "listener-watchdog"
        private const val WATCHDOG_NOTIFICATION_ID = 1003

        fun schedule(context: Context) {
            // 15 minutes is WorkManager's floor for periodic work.
            val request = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }.onFailure { Log.e(TAG, "Could not schedule watchdog: ${it.message}") }
        }
    }
}
