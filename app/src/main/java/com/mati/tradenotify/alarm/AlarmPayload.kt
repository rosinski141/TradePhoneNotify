package com.mati.tradenotify.alarm

import android.content.Intent

/** Everything the ringing alarm needs, carried on the intent so no database read is needed. */
data class AlarmPayload(
    val eventId: Long,
    val channel: String,
    val text: String,
    val ruleName: String,
    val soundUri: String?,
    val vibrate: Boolean,
    val postedAt: Long,
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_EVENT_ID, eventId)
        putExtra(EXTRA_CHANNEL, channel)
        putExtra(EXTRA_TEXT, text)
        putExtra(EXTRA_RULE_NAME, ruleName)
        putExtra(EXTRA_SOUND_URI, soundUri)
        putExtra(EXTRA_VIBRATE, vibrate)
        putExtra(EXTRA_POSTED_AT, postedAt)
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_RULE_NAME = "rule_name"
        private const val EXTRA_SOUND_URI = "sound_uri"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val EXTRA_POSTED_AT = "posted_at"

        fun from(intent: Intent?): AlarmPayload? {
            if (intent == null || !intent.hasExtra(EXTRA_CHANNEL)) return null
            return AlarmPayload(
                eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L),
                channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty(),
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                ruleName = intent.getStringExtra(EXTRA_RULE_NAME).orEmpty(),
                soundUri = intent.getStringExtra(EXTRA_SOUND_URI),
                vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
                postedAt = intent.getLongExtra(EXTRA_POSTED_AT, System.currentTimeMillis()),
            )
        }
    }
}
