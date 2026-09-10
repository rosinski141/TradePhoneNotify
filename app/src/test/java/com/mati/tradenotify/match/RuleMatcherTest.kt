package com.mati.tradenotify.match

import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.ingest.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private val now = 1_700_000_000_000L

    private fun rule(
        id: Long = 1,
        name: String = "FX",
        channelMatch: String = "FX Signals Pro",
        mode: MatchMode = MatchMode.EXACT,
        includes: List<String> = listOf("BUY", "SELL"),
        requireAll: Boolean = false,
        excludes: List<String> = emptyList(),
        cooldownSeconds: Int = 60,
        lastFiredAt: Long = 0,
        enabled: Boolean = true,
        packageFilter: String? = null,
    ) = RuleEntity(
        id = id,
        enabled = enabled,
        name = name,
        channelMatch = channelMatch,
        channelMatchMode = mode,
        packageFilter = packageFilter,
        includeKeywords = includes,
        requireAllIncludes = requireAll,
        excludeKeywords = excludes,
        cooldownSeconds = cooldownSeconds,
        lastFiredAt = lastFiredAt,
    )

    private fun signal(
        channel: String = "FX Signals Pro",
        text: String = "BUY EURUSD @ 1.0840",
        pkg: String = "org.telegram.messenger",
    ) = Signal(packageName = pkg, channel = channel, text = text, postedAt = now)

    @Test
    fun `fires when channel and keyword match`() {
        val outcome = RuleMatcher.match(signal(), listOf(rule()), now)
        assertTrue(outcome is MatchOutcome.Fire)
    }

    @Test
    fun `keyword matching is case insensitive`() {
        val outcome = RuleMatcher.match(signal(text = "buy eurusd now"), listOf(rule()), now)
        assertTrue(outcome is MatchOutcome.Fire)
    }

    @Test
    fun `no match when keyword absent`() {
        val outcome = RuleMatcher.match(
            signal(text = "Market closed for the weekend"),
            listOf(rule()),
            now,
        )
        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `empty include list matches every message`() {
        val outcome = RuleMatcher.match(
            signal(text = "anything at all"),
            listOf(rule(includes = emptyList())),
            now,
        )
        assertTrue(outcome is MatchOutcome.Fire)
    }

    @Test
    fun `exclude keyword suppresses an otherwise matching message`() {
        val outcome = RuleMatcher.match(
            signal(text = "BUY EURUSD — TP HIT, closing"),
            listOf(rule(excludes = listOf("TP HIT"))),
            now,
        )
        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `requireAll demands every include keyword`() {
        val r = rule(includes = listOf("BUY", "EURUSD"), requireAll = true)
        assertTrue(RuleMatcher.match(signal(text = "BUY EURUSD now"), listOf(r), now) is MatchOutcome.Fire)
        assertEquals(
            MatchOutcome.NoMatch,
            RuleMatcher.match(signal(text = "BUY GBPUSD now"), listOf(r), now),
        )
    }

    @Test
    fun `channel exact mode rejects a different channel`() {
        val outcome = RuleMatcher.match(signal(channel = "Other Channel"), listOf(rule()), now)
        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `channel contains mode matches a partial title`() {
        val r = rule(channelMatch = "Signals", mode = MatchMode.CONTAINS)
        val outcome = RuleMatcher.match(signal(channel = "FX Signals Pro"), listOf(r), now)
        assertTrue(outcome is MatchOutcome.Fire)
    }

    @Test
    fun `channel regex mode matches`() {
        val r = rule(channelMatch = "^FX .*Pro$", mode = MatchMode.REGEX)
        val outcome = RuleMatcher.match(signal(channel = "FX Signals Pro"), listOf(r), now)
        assertTrue(outcome is MatchOutcome.Fire)
    }

    @Test
    fun `an invalid regex never matches instead of crashing`() {
        val r = rule(channelMatch = "([unclosed", mode = MatchMode.REGEX)
        assertEquals(MatchOutcome.NoMatch, RuleMatcher.match(signal(), listOf(r), now))
    }

    @Test
    fun `slash wrapped keyword is treated as a regex`() {
        val r = rule(includes = listOf("""/^BUY\s+\w+/"""))
        assertTrue(RuleMatcher.match(signal(text = "BUY EURUSD"), listOf(r), now) is MatchOutcome.Fire)
        assertEquals(
            MatchOutcome.NoMatch,
            RuleMatcher.match(signal(text = "Please BUY EURUSD"), listOf(r), now),
        )
    }

    @Test
    fun `substring keyword does not accidentally behave as a regex`() {
        // A bare dot must stay literal, otherwise "S.L" would match "SELL".
        assertFalse(RuleMatcher.matchesKeyword("SELL", "S.L"))
        assertTrue(RuleMatcher.matchesKeyword("S.L 1.0810", "S.L"))
    }

    @Test
    fun `cooldown suppresses a second alarm from the same rule`() {
        val r = rule(cooldownSeconds = 60, lastFiredAt = now - 30_000)
        val outcome = RuleMatcher.match(signal(), listOf(r), now)
        assertTrue(outcome is MatchOutcome.Cooldown)
        assertEquals(30_000L, (outcome as MatchOutcome.Cooldown).remainingMs)
    }

    @Test
    fun `cooldown expires`() {
        val r = rule(cooldownSeconds = 60, lastFiredAt = now - 61_000)
        assertTrue(RuleMatcher.match(signal(), listOf(r), now) is MatchOutcome.Fire)
    }

    @Test
    fun `a second rule still fires while the first is cooling down`() {
        val cooling = rule(id = 1, name = "A", lastFiredAt = now - 1_000)
        val ready = rule(id = 2, name = "B", includes = listOf("EURUSD"))
        val outcome = RuleMatcher.match(signal(), listOf(cooling, ready), now)
        assertTrue(outcome is MatchOutcome.Fire)
        assertEquals("B", (outcome as MatchOutcome.Fire).rule.name)
    }

    @Test
    fun `a clock moving backwards does not wedge a rule shut`() {
        val r = rule(lastFiredAt = now + 10 * 60_000)
        assertTrue(RuleMatcher.match(signal(), listOf(r), now) is MatchOutcome.Fire)
    }

    @Test
    fun `disabled rules are ignored`() {
        assertEquals(
            MatchOutcome.NoMatch,
            RuleMatcher.match(signal(), listOf(rule(enabled = false)), now),
        )
    }

    @Test
    fun `package filter restricts the source app`() {
        val r = rule(packageFilter = "org.thunderdog.challegram")
        assertEquals(MatchOutcome.NoMatch, RuleMatcher.match(signal(), listOf(r), now))
        assertTrue(
            RuleMatcher.match(
                signal(pkg = "org.thunderdog.challegram"),
                listOf(r),
                now,
            ) is MatchOutcome.Fire,
        )
    }

    @Test
    fun `a blank channel matches any chat, in every mode`() {
        // The broadest rule you can write: no channel filter at all, keywords only.
        MatchMode.entries.forEach { mode ->
            val r = rule(channelMatch = "", mode = mode)
            assertTrue(
                "blank channel in $mode mode should match",
                RuleMatcher.match(signal(channel = "Anything At All"), listOf(r), now)
                    is MatchOutcome.Fire,
            )
        }
    }

    @Test
    fun `a blank channel still respects the keyword filters`() {
        val r = rule(channelMatch = "", includes = listOf("BUY"))
        assertEquals(
            MatchOutcome.NoMatch,
            RuleMatcher.match(signal(channel = "Some Chat", text = "good morning"), listOf(r), now),
        )
    }

    @Test
    fun `a whitespace-only channel counts as blank`() {
        val r = rule(channelMatch = "   ", mode = MatchMode.EXACT)
        assertTrue(RuleMatcher.match(signal(channel = "Whatever"), listOf(r), now) is MatchOutcome.Fire)
    }

    @Test
    fun `channel comparison tolerates surrounding whitespace`() {
        val outcome = RuleMatcher.match(signal(channel = "  FX Signals Pro "), listOf(rule()), now)
        assertTrue(outcome is MatchOutcome.Fire)
    }
}
