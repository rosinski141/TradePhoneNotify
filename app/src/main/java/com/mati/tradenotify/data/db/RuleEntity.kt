package com.mati.tradenotify.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** How a rule's [RuleEntity.channelMatch] is compared against the channel title. */
enum class MatchMode { EXACT, CONTAINS, REGEX }

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean = true,
    val name: String,
    /** Compared against the channel title according to [channelMatchMode]. */
    val channelMatch: String,
    val channelMatchMode: MatchMode = MatchMode.CONTAINS,
    /** Restrict to one Telegram variant, or null for any watched package. */
    val packageFilter: String? = null,
    /**
     * Keywords the message must contain. Empty means "every post in this channel".
     * A keyword wrapped in slashes (`/^BUY\s/`) is treated as a regex instead of a substring.
     */
    val includeKeywords: List<String> = emptyList(),
    /** false = ANY of [includeKeywords] is enough; true = all of them must be present. */
    val requireAllIncludes: Boolean = false,
    /** If any of these is present the message is ignored. Same `/regex/` support. */
    val excludeKeywords: List<String> = emptyList(),
    /** Minimum gap between two alarms from this rule. */
    val cooldownSeconds: Int = 60,
    /** Alarm sound; null uses the system default alarm tone. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    /**
     * When this rule last fired, epoch millis. Persisted rather than held in memory because the
     * listener process is killed and restarted freely by the system — an in-memory cooldown would
     * reset every time and let a burst through.
     */
    val lastFiredAt: Long = 0,
)
