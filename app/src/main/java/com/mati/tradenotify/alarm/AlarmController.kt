package com.mati.tradenotify.alarm

import android.content.Context
import com.mati.tradenotify.data.SettingsStore
import com.mati.tradenotify.match.QuietHours
import com.mati.tradenotify.util.minutesOfDay

/** Decides whether an alarm is allowed to sound right now, and starts it. */
class AlarmController(
    private val context: Context,
    private val settings: SettingsStore,
) {

    suspend fun isQuietNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = runCatching { settings.current() }.getOrNull() ?: return false
        if (!s.quietHoursEnabled) return false
        return QuietHours.isQuiet(minutesOfDay(nowMs), s.quietStartMinutes, s.quietEndMinutes)
    }

    fun fire(payload: AlarmPayload) {
        AlarmService.start(context, payload)
    }

    /** Used by the "Test alarm" button so the real path is exercised, not a mock of it. */
    fun fireTest() {
        fire(
            AlarmPayload(
                eventId = -1L,
                channel = "Test channel",
                text = "BUY EURUSD @ 1.0840  SL 1.0810  TP 1.0900\n\n" +
                    "This is a test alarm. Dismiss or snooze it the same way you would a real one.",
                ruleName = "Test",
                soundUri = null,
                vibrate = true,
                postedAt = System.currentTimeMillis(),
            ),
        )
    }
}
