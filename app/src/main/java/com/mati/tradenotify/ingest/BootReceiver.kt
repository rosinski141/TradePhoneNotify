package com.mati.tradenotify.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mati.tradenotify.util.TAG

/**
 * Wakes the app after a reboot.
 *
 * The system re-binds the notification listener by itself, but receiving this broadcast is what
 * runs `Application.onCreate` and therefore re-arms the watchdog.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        Log.i(TAG, "Boot/update received; re-arming watchdog")
        ListenerWatchdogWorker.schedule(context)
    }
}
