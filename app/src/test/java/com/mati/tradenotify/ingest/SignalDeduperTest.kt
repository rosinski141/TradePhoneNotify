package com.mati.tradenotify.ingest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDeduperTest {

    private val start = 1_700_000_000_000L

    private fun signal(text: String = "BUY EURUSD", channel: String = "FX Signals Pro") =
        Signal("org.telegram.messenger", channel, text, start)

    @Test
    fun `first sighting is accepted`() {
        assertTrue(SignalDeduper().accept(signal(), start))
    }

    @Test
    fun `an immediate repost is suppressed`() {
        val deduper = SignalDeduper()
        assertTrue(deduper.accept(signal(), start))
        assertFalse(deduper.accept(signal(), start + 1_000))
        assertFalse(deduper.accept(signal(), start + 9_999))
    }

    @Test
    fun `the same text is accepted again after the window`() {
        val deduper = SignalDeduper(windowMs = 10_000)
        assertTrue(deduper.accept(signal(), start))
        assertTrue(deduper.accept(signal(), start + 10_001))
    }

    @Test
    fun `different text from the same channel is not a duplicate`() {
        val deduper = SignalDeduper()
        assertTrue(deduper.accept(signal(text = "BUY EURUSD"), start))
        assertTrue(deduper.accept(signal(text = "SELL GBPUSD"), start + 100))
    }

    @Test
    fun `identical text from different channels is not a duplicate`() {
        val deduper = SignalDeduper()
        assertTrue(deduper.accept(signal(channel = "A"), start))
        assertTrue(deduper.accept(signal(channel = "B"), start + 100))
    }

    @Test
    fun `entries are bounded`() {
        val deduper = SignalDeduper(windowMs = 60_000, maxEntries = 8)
        repeat(50) { i ->
            assertTrue(deduper.accept(signal(text = "signal $i"), start + i))
        }
        // The oldest keys were evicted, so an early one is treated as new again.
        assertTrue(deduper.accept(signal(text = "signal 0"), start + 100))
    }
}
