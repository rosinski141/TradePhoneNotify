package com.mati.tradenotify.match

import com.mati.tradenotify.data.db.ChannelSummary
import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelHealthTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun rule(
        id: Long = 1,
        name: String = "FX",
        channelMatch: String = "FX Signals Pro",
        mode: MatchMode = MatchMode.EXACT,
        enabled: Boolean = true,
    ) = RuleEntity(
        id = id,
        enabled = enabled,
        name = name,
        channelMatch = channelMatch,
        channelMatchMode = mode,
    )

    private fun channel(name: String = "FX Signals Pro", lastSeen: Long = now) =
        ChannelSummary(name, "org.telegram.messenger", lastSeen, 5)

    @Test
    fun `a recently active channel is not flagged`() {
        val quiet = ChannelHealth.findQuiet(
            listOf(rule()),
            listOf(channel(lastSeen = now - 60_000)),
            now,
        )
        assertTrue(quiet.isEmpty())
    }

    @Test
    fun `a channel silent for more than a day is flagged with its last sighting`() {
        val lastSeen = now - 2 * day
        val quiet = ChannelHealth.findQuiet(listOf(rule()), listOf(channel(lastSeen = lastSeen)), now)
        assertEquals(1, quiet.size)
        assertEquals(lastSeen, quiet.first().lastSeen)
    }

    @Test
    fun `a rule whose channel was never seen is flagged with no timestamp`() {
        // The classic mistyped-channel failure: other channels arrive, this rule's never does.
        val quiet = ChannelHealth.findQuiet(
            listOf(rule(channelMatch = "FX Signals Prro")),
            listOf(channel()),
            now,
        )
        assertEquals(1, quiet.size)
        assertNull(quiet.first().lastSeen)
    }

    @Test
    fun `nothing is flagged before any channel has been observed`() {
        // With an empty log the real story is "finish setup", not "a channel went quiet".
        assertTrue(ChannelHealth.findQuiet(listOf(rule()), emptyList(), now).isEmpty())
    }

    @Test
    fun `disabled rules are not flagged`() {
        val quiet = ChannelHealth.findQuiet(
            listOf(rule(enabled = false, channelMatch = "Never seen")),
            listOf(channel()),
            now,
        )
        assertTrue(quiet.isEmpty())
    }

    @Test
    fun `a contains rule is satisfied by any matching channel`() {
        val quiet = ChannelHealth.findQuiet(
            listOf(rule(channelMatch = "Signals", mode = MatchMode.CONTAINS)),
            listOf(channel(name = "FX Signals Pro", lastSeen = now - 1000)),
            now,
        )
        assertTrue(quiet.isEmpty())
    }

    @Test
    fun `the most recent matching channel decides`() {
        val quiet = ChannelHealth.findQuiet(
            listOf(rule(channelMatch = "Signals", mode = MatchMode.CONTAINS)),
            listOf(
                channel(name = "FX Signals Pro", lastSeen = now - 5 * day),
                channel(name = "Gold Signals", lastSeen = now - 1000),
            ),
            now,
        )
        assertTrue(quiet.isEmpty())
    }
}
