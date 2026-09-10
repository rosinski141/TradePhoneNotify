package com.mati.tradenotify.data

import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity

/**
 * Starting points for a new rule.
 *
 * Typing keyword lists by hand is where most people give up, and a rule with a typo in it fails
 * silently — it simply never rings. A preset gets you a working rule in one tap, leaving only the
 * channel name to fill in.
 */
data class RulePreset(
    val id: String,
    val title: String,
    val summary: String,
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>,
) {
    fun toRule(channel: String): RuleEntity = RuleEntity(
        name = if (channel.isBlank()) title else channel,
        channelMatch = channel.trim(),
        channelMatchMode = if (channel.isBlank()) MatchMode.CONTAINS else MatchMode.EXACT,
        includeKeywords = includeKeywords,
        excludeKeywords = excludeKeywords,
    )
}

object RulePresets {

    val ALL: List<RulePreset> = listOf(
        RulePreset(
            id = "forex",
            title = "Forex signals",
            summary = "Entries on FX pairs; ignores results and follow-ups.",
            includeKeywords = listOf("BUY", "SELL", "ENTRY", "LONG", "SHORT"),
            excludeKeywords = listOf("TP HIT", "SL HIT", "CLOSED", "RESULT", "BREAKEVEN"),
        ),
        RulePreset(
            id = "crypto",
            title = "Crypto signals",
            summary = "Spot and futures entries; ignores updates on open trades.",
            includeKeywords = listOf("BUY", "SELL", "LONG", "SHORT", "ENTRY", "ACCUMULATE"),
            excludeKeywords = listOf("TP HIT", "TAKE PROFIT REACHED", "CLOSED", "RESULT", "UPDATE"),
        ),
        RulePreset(
            id = "urgent",
            title = "Urgent only",
            summary = "Just the messages flagged as urgent by the channel.",
            includeKeywords = listOf("URGENT", "NOW", "IMMEDIATE", "ALERT"),
            excludeKeywords = emptyList(),
        ),
        RulePreset(
            id = "everything",
            title = "Every message",
            summary = "Alarms on anything posted. Best for a low-traffic channel.",
            includeKeywords = emptyList(),
            excludeKeywords = emptyList(),
        ),
    )

    fun byId(id: String): RulePreset? = ALL.firstOrNull { it.id == id }
}
