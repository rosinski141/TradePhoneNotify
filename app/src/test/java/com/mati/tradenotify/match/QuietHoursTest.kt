package com.mati.tradenotify.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    private val tenPm = 22 * 60
    private val sevenAm = 7 * 60

    @Test
    fun `window that wraps midnight silences the night`() {
        assertTrue(QuietHours.isQuiet(23 * 60, tenPm, sevenAm))
        assertTrue(QuietHours.isQuiet(2 * 60, tenPm, sevenAm))
        assertTrue(QuietHours.isQuiet(tenPm, tenPm, sevenAm))
    }

    @Test
    fun `window that wraps midnight leaves the day alone`() {
        assertFalse(QuietHours.isQuiet(12 * 60, tenPm, sevenAm))
        assertFalse(QuietHours.isQuiet(sevenAm, tenPm, sevenAm))
    }

    @Test
    fun `same day window`() {
        assertTrue(QuietHours.isQuiet(10 * 60, 9 * 60, 17 * 60))
        assertFalse(QuietHours.isQuiet(18 * 60, 9 * 60, 17 * 60))
        assertFalse(QuietHours.isQuiet(8 * 60, 9 * 60, 17 * 60))
    }

    @Test
    fun `an empty window silences nothing`() {
        assertFalse(QuietHours.isQuiet(9 * 60, 9 * 60, 9 * 60))
        assertFalse(QuietHours.isQuiet(3 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun `formats as HH mm`() {
        assertEquals("22:00", QuietHours.format(tenPm))
        assertEquals("07:05", QuietHours.format(7 * 60 + 5))
        assertEquals("00:00", QuietHours.format(0))
    }
}
