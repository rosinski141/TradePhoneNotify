package com.mati.tradenotify.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What the pipeline decided to do with an observed message. */
enum class EventOutcome {
    /** A rule matched and the alarm was started. */
    ALARMED,

    /** A rule matched but it had fired too recently. */
    COOLDOWN,

    /** A rule matched but quiet hours were in effect. */
    QUIET_HOURS,

    /** Seen, but no enabled rule wanted it. */
    NO_MATCH,
}

/**
 * Every message seen in a watched app is recorded here, matched or not.
 *
 * Logging the misses is what makes the app debuggable: it powers channel discovery (you pick a
 * real channel name instead of typing it) and answers "why didn't my rule fire?".
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postedAt: Long,
    val packageName: String,
    val channel: String,
    val text: String,
    val outcome: EventOutcome,
    val matchedRuleId: Long? = null,
    val matchedRuleName: String? = null,
)
