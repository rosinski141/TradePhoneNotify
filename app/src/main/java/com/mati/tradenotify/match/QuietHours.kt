package com.mati.tradenotify.match

/**
 * Quiet hours, expressed as minutes from midnight. The window is `[start, end)` and may wrap past
 * midnight (e.g. start 22:00, end 07:00 silences overnight).
 */
object QuietHours {

    /** @param nowMinutes minutes since local midnight, 0..1439. */
    fun isQuiet(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        // An empty window silences nothing.
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes >= startMinutes && nowMinutes < endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun format(minutes: Int): String {
        val h = (minutes / 60).coerceIn(0, 23)
        val m = (minutes % 60).coerceIn(0, 59)
        return "%02d:%02d".format(h, m)
    }
}
