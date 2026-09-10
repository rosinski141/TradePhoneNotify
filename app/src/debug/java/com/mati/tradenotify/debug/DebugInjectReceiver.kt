package com.mati.tradenotify.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mati.tradenotify.TradeNotifyApp
import com.mati.tradenotify.data.TelegramPackages
import com.mati.tradenotify.ingest.Signal
import com.mati.tradenotify.util.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only entry point for injecting a synthetic signal from adb.
 *
 * It feeds [com.mati.tradenotify.ingest.SignalPipeline] directly, so a test exercises the real
 * matching, cooldown, logging and alarm code — everything except Telegram itself.
 *
 * ```
 * adb shell am broadcast -n com.mati.tradenotify.debug/com.mati.tradenotify.debug.DebugInjectReceiver \
 *   -a com.mati.tradenotify.DEBUG_INJECT \
 *   -e channel "FX Signals Pro" \
 *   -e text "BUY EURUSD @ 1.0840 SL 1.0810 TP 1.0900"
 * ```
 */
class DebugInjectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val signal = Signal(
            packageName = intent.getStringExtra("pkg") ?: TelegramPackages.OFFICIAL,
            channel = intent.getStringExtra("channel") ?: "Test channel",
            text = intent.getStringExtra("text") ?: "BUY EURUSD @ 1.0840",
            postedAt = System.currentTimeMillis(),
        )
        Log.i(TAG, "DEBUG inject: [${signal.channel}] ${signal.text}")

        val pipeline = TradeNotifyApp.container(context).pipeline
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                pipeline.onSignal(signal)
            } catch (t: Throwable) {
                Log.e(TAG, "DEBUG inject failed: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.mati.tradenotify.DEBUG_INJECT"
    }
}
