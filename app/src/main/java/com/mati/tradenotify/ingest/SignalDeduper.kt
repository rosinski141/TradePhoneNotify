package com.mati.tradenotify.ingest

/**
 * Suppresses repeats of the same message.
 *
 * Telegram re-posts a notification whenever it updates one (read state, edits, a sibling message
 * arriving), so `onNotificationPosted` fires several times for a single signal. Without this a
 * single trading signal would start the alarm two or three times over.
 */
class SignalDeduper(
    private val windowMs: Long = 10_000L,
    private val maxEntries: Int = 64,
) {
    private val seen = LinkedHashMap<String, Long>()

    /** @return true if this signal is new and should be processed. */
    @Synchronized
    fun accept(signal: Signal, nowMs: Long = System.currentTimeMillis()): Boolean {
        purge(nowMs)
        val key = "${signal.packageName}|${signal.channel}|${signal.text}"
        val previous = seen[key]
        if (previous != null && nowMs - previous < windowMs) return false
        seen[key] = nowMs
        if (seen.size > maxEntries) {
            val oldest = seen.keys.firstOrNull()
            if (oldest != null) seen.remove(oldest)
        }
        return true
    }

    private fun purge(nowMs: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value >= windowMs) iterator.remove()
        }
    }
}
