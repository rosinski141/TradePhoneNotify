package com.mati.tradenotify.match

import com.mati.tradenotify.data.db.ChannelSummary
import com.mati.tradenotify.data.db.RuleEntity

/** A rule that has heard nothing lately, with when its channel was last seen (null = never). */
data class QuietRule(val rule: RuleEntity, val lastSeen: Long?)

object ChannelHealth {

    /** How long a watched channel may stay silent before it is worth mentioning. */
    const val THRESHOLD_MS = 24 * 60 * 60 * 1000L

    /**
     * Enabled rules whose channel has produced nothing recently.
     *
     * A muted channel or a mistyped channel name fails silently — the app looks perfectly healthy
     * and simply never rings. Comparing the configured rules against what was actually observed is
     * the only way to surface that before a signal is missed.
     */
    fun findQuiet(
        rules: List<RuleEntity>,
        channels: List<ChannelSummary>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<QuietRule> {
        // Nothing observed at all is the "finish setup" story, not the "channel went quiet" one.
        if (channels.isEmpty()) return emptyList()

        return rules.filter { it.enabled }.mapNotNull { rule ->
            val lastSeen = channels
                .filter { RuleMatcher.channelMatches(rule, it.channel) }
                .maxOfOrNull { it.lastSeen }
            when {
                lastSeen == null -> QuietRule(rule, null)
                nowMs - lastSeen > THRESHOLD_MS -> QuietRule(rule, lastSeen)
                else -> null
            }
        }
    }
}
