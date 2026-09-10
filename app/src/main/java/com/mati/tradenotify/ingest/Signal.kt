package com.mati.tradenotify.ingest

/**
 * One message observed in a watched Telegram-like app.
 *
 * This is the single currency of the whole pipeline. The notification listener produces these
 * today; a TDLib/MTProto backend could produce identical ones tomorrow without anything
 * downstream (matching, alarm, UI) needing to change.
 */
data class Signal(
    /** Package the message was observed in, e.g. `org.telegram.messenger`. */
    val packageName: String,
    /** Channel / chat title as shown by Telegram. */
    val channel: String,
    /** Message body, as complete as the source could provide. */
    val text: String,
    /** Wall-clock time the message was observed, epoch millis. */
    val postedAt: Long,
)

/** Anything that can consume signals. [com.mati.tradenotify.ingest.SignalPipeline] is the one implementation. */
fun interface SignalSink {
    suspend fun onSignal(signal: Signal)
}
