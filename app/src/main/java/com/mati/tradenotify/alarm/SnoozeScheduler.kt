package com.mati.tradenotify.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mati.tradenotify.ui.MainActivity
import com.mati.tradenotify.util.TAG

object SnoozeScheduler {

    private const val REQUEST_CODE = 42

    fun schedule(context: Context, payload: AlarmPayload, minutes: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L

        val fire = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            payload.writeTo(Intent(context, SnoozeReceiver::class.java)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // setAlarmClock is the only variant guaranteed to fire exactly through Doze, which is
        // precisely the state the phone is in when a snoozed signal alarm matters.
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
            Log.i(TAG, "Snoozed '${payload.channel}' for $minutes min")
        }.onFailure { Log.e(TAG, "Could not schedule snooze: ${it.message}") }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, SnoozeReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(fire)
    }
}

/** Re-raises the alarm when a snooze expires. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = AlarmPayload.from(intent) ?: return
        Log.i(TAG, "Snooze expired for '${payload.channel}'")
        AlarmService.start(context, payload)
    }
}
