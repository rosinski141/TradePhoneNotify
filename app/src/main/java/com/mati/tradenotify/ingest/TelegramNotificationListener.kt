package com.mati.tradenotify.ingest

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.mati.tradenotify.TradeNotifyApp
import com.mati.tradenotify.data.TelegramPackages
import com.mati.tradenotify.util.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Reads Telegram's own notifications and feeds them into [SignalPipeline].
 *
 * This is the whole ingestion story: no Telegram credentials, no bot, no api_id — which is why it
 * works for channels the user merely subscribes to.
 */
class TelegramNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deduper = SignalDeduper()

    @Volatile
    private var watched: Set<String> = TelegramPackages.DEFAULT_WATCHED

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        _connected.value = true

        // Cached rather than read per notification: onNotificationPosted runs on the main thread
        // and fires often.
        scope.launch {
            TradeNotifyApp.container(this@TelegramNotificationListener).settings.settings
                .collect { watched = it.watchedPackages }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected; requesting rebind")
        _connected.value = false
        // Android unbinds listeners on app update, low memory, and some OEM battery sweeps.
        // Asking for a rebind is what turns an outage into a blip.
        runCatching {
            requestRebind(ComponentName(this, TelegramNotificationListener::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName !in watched) return

        // Runs on the main thread, and the StatusBarNotification may be recycled once this
        // returns — so parse now and hand only the immutable Signal to the background.
        val signal = runCatching {
            NotificationTextExtractor.extract(sbn.packageName, notification, sbn.postTime)
        }.getOrElse {
            Log.w(TAG, "Could not parse notification: ${it.message}")
            null
        } ?: return

        if (!deduper.accept(signal)) {
            Log.d(TAG, "Duplicate suppressed for '${signal.channel}'")
            return
        }

        val pipeline = TradeNotifyApp.container(this).pipeline
        scope.launch {
            runCatching { pipeline.onSignal(signal) }
                .onFailure { Log.e(TAG, "Pipeline failed: ${it.message}", it) }
        }
    }

    override fun onDestroy() {
        _connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _connected = MutableStateFlow(false)

        /** Whether the system currently has us bound. Surfaced on the home screen. */
        val connected: StateFlow<Boolean> = _connected
    }
}
