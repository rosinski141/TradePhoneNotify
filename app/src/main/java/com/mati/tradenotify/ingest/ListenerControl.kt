package com.mati.tradenotify.ingest

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.mati.tradenotify.util.TAG

object ListenerControl {

    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * Nudges the system to bind the listener if permission is granted but we aren't connected.
     *
     * Granting notification access does not itself deliver a binding — the system may take its
     * time, and after a force-stop or an app update it may not rebind until something asks. Doing
     * this on resume turns "wait up to 15 minutes for the watchdog" into an immediate reconnect,
     * which matters because a granted-but-unbound listener looks healthy and silently rings nothing.
     */
    fun requestRebindIfNeeded(context: Context) {
        if (!isGranted(context)) return
        if (TelegramNotificationListener.connected.value) return
        val component = ComponentName(
            context.applicationContext,
            TelegramNotificationListener::class.java,
        )
        runCatching { NotificationListenerService.requestRebind(component) }
            .onSuccess { Log.i(TAG, "Requested listener rebind") }
            .onFailure { Log.e(TAG, "requestRebind failed: ${it.message}") }
    }
}
