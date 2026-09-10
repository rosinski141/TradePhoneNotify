package com.mati.tradenotify.data

import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBackupCodecTest {

    private val rule = RuleEntity(
        id = 7,
        enabled = true,
        name = "FX",
        channelMatch = "FX Signals Pro",
        channelMatchMode = MatchMode.CONTAINS,
        packageFilter = "org.telegram.messenger",
        includeKeywords = listOf("BUY", "SELL"),
        requireAllIncludes = true,
        excludeKeywords = listOf("TP HIT"),
        cooldownSeconds = 90,
        vibrate = false,
        lastFiredAt = 1_700_000_000_000,
    )

    @Test
    fun `round trip preserves what the rule means`() {
        val restored = RuleBackupCodec.import(RuleBackupCodec.export(listOf(rule)))
        assertNotNull(restored)
        val r = restored!!.single()
        assertEquals(rule.name, r.name)
        assertEquals(rule.channelMatch, r.channelMatch)
        assertEquals(rule.channelMatchMode, r.channelMatchMode)
        assertEquals(rule.packageFilter, r.packageFilter)
        assertEquals(rule.includeKeywords, r.includeKeywords)
        assertEquals(rule.requireAllIncludes, r.requireAllIncludes)
        assertEquals(rule.excludeKeywords, r.excludeKeywords)
        assertEquals(rule.cooldownSeconds, r.cooldownSeconds)
        assertEquals(rule.vibrate, r.vibrate)
        assertEquals(rule.enabled, r.enabled)
    }

    @Test
    fun `local bookkeeping is not carried across`() {
        // id 0 makes the import an insert; a stale lastFiredAt would silently mute the new rule
        // for its whole cooldown on the phone it lands on.
        val r = RuleBackupCodec.import(RuleBackupCodec.export(listOf(rule)))!!.single()
        assertEquals(0L, r.id)
        assertEquals(0L, r.lastFiredAt)
    }

    @Test
    fun `several rules survive together`() {
        val rules = listOf(rule, rule.copy(id = 8, name = "Crypto", channelMatch = "Crypto Pro"))
        val restored = RuleBackupCodec.import(RuleBackupCodec.export(rules))!!
        assertEquals(2, restored.size)
        assertEquals(listOf("FX", "Crypto"), restored.map { it.name })
    }

    @Test
    fun `a file that isn't a backup returns null`() {
        assertNull(RuleBackupCodec.import("this is not json"))
        assertNull(RuleBackupCodec.import(""))
    }

    @Test
    fun `unknown fields from a newer build are ignored`() {
        val json = """
            {"version":1,"exported_at":0,"rules":[
              {"name":"X","channelMatch":"Y","futureField":"whatever"}
            ]}
        """.trimIndent()
        val restored = RuleBackupCodec.import(json)
        assertNotNull(restored)
        assertEquals("X", restored!!.single().name)
    }

    @Test
    fun `an unrecognised match mode falls back rather than throwing`() {
        val json = """{"rules":[{"name":"X","channelMatch":"Y","channelMatchMode":"SIDEWAYS"}]}"""
        assertEquals(MatchMode.EXACT, RuleBackupCodec.import(json)!!.single().channelMatchMode)
    }

    @Test
    fun `exported text is human readable json`() {
        val text = RuleBackupCodec.export(listOf(rule))
        assertTrue(text.contains("FX Signals Pro"))
        assertTrue(text.contains("\n"))
    }
}
