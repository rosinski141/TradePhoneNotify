package com.mati.tradenotify.match

import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.ingest.Signal

/** What [RuleMatcher] decided about a signal. */
sealed interface MatchOutcome {
    /** [rule] wants this message and is off cooldown. */
    data class Fire(val rule: RuleEntity) : MatchOutcome

    /** [rule] wanted it but fired too recently. */
    data class Cooldown(val rule: RuleEntity, val remainingMs: Long) : MatchOutcome

    /** No enabled rule wanted it. */
    data object NoMatch : MatchOutcome
}

/**
 * Decides whether a [Signal] should raise an alarm.
 *
 * Deliberately free of Android imports so it can be exercised by plain JUnit — this is the piece
 * most likely to be wrong, and the piece hardest to debug on a phone at 3am.
 */
object RuleMatcher {

    /**
     * @param rules only enabled rules should be passed in; order defines priority.
     * @param nowMs current time, injected so tests are deterministic.
     */
    fun match(signal: Signal, rules: List<RuleEntity>, nowMs: Long): MatchOutcome {
        val candidates = rules.filter { it.enabled && wants(it, signal) }
        if (candidates.isEmpty()) return MatchOutcome.NoMatch

        // Prefer any candidate that is off cooldown, so a second rule on the same channel still
        // fires while the first is cooling down.
        val ready = candidates.firstOrNull { remainingCooldownMs(it, nowMs) <= 0L }
        if (ready != null) return MatchOutcome.Fire(ready)

        val first = candidates.first()
        return MatchOutcome.Cooldown(first, remainingCooldownMs(first, nowMs))
    }

    private fun remainingCooldownMs(rule: RuleEntity, nowMs: Long): Long {
        if (rule.cooldownSeconds <= 0) return 0L
        val elapsed = nowMs - rule.lastFiredAt
        // A clock that moved backwards shouldn't wedge a rule shut.
        if (elapsed < 0) return 0L
        return (rule.cooldownSeconds * 1000L - elapsed).coerceAtLeast(0L)
    }

    /** Channel + keyword test, ignoring cooldown. */
    fun wants(rule: RuleEntity, signal: Signal): Boolean {
        if (rule.packageFilter != null && rule.packageFilter != signal.packageName) return false
        if (!channelMatches(rule, signal.channel)) return false

        val text = signal.text
        if (rule.excludeKeywords.any { matchesKeyword(text, it) }) return false

        if (rule.includeKeywords.isEmpty()) return true
        return if (rule.requireAllIncludes) {
            rule.includeKeywords.all { matchesKeyword(text, it) }
        } else {
            rule.includeKeywords.any { matchesKeyword(text, it) }
        }
    }

    /** Exposed so the home screen can tell which watched channels have gone quiet. */
    fun channelMatches(rule: RuleEntity, channel: String): Boolean {
        val target = channel.trim()
        val pattern = rule.channelMatch.trim()
        if (pattern.isEmpty()) return true
        return when (rule.channelMatchMode) {
            MatchMode.EXACT -> target.equals(pattern, ignoreCase = true)
            MatchMode.CONTAINS -> target.contains(pattern, ignoreCase = true)
            MatchMode.REGEX -> compile(pattern)?.containsMatchIn(target) ?: false
        }
    }

    /**
     * Case-insensitive substring test, unless the keyword is wrapped in slashes (`/^BUY\b/`) in
     * which case it is treated as a regex. This gives power users regexes without forcing a mode
     * switch onto everyone else.
     */
    fun matchesKeyword(text: String, keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        val asRegex = k.length >= 2 && k.startsWith('/') && k.endsWith('/')
        return if (asRegex) {
            compile(k.substring(1, k.length - 1))?.containsMatchIn(text) ?: false
        } else {
            text.contains(k, ignoreCase = true)
        }
    }

    /** An invalid pattern must never crash the listener; it simply never matches. */
    private fun compile(pattern: String): Regex? =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
}
