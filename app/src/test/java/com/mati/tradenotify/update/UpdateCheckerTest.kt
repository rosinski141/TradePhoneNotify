package com.mati.tradenotify.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a higher version is newer`() {
        assertTrue(UpdateChecker.isNewer("1.0", "1.1"))
        assertTrue(UpdateChecker.isNewer("1.0", "2.0"))
    }

    @Test
    fun `versions are compared numerically, not as strings`() {
        // The one that a naive string comparison gets backwards.
        assertTrue(UpdateChecker.isNewer("1.9", "1.10"))
        assertFalse(UpdateChecker.isNewer("1.10", "1.9"))
        assertTrue(UpdateChecker.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0"))
        assertFalse(UpdateChecker.isNewer("2.3.1", "2.3.1"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(UpdateChecker.isNewer("2.0", "1.9"))
    }

    @Test
    fun `a leading v is ignored`() {
        assertTrue(UpdateChecker.isNewer("1.0", "v1.1"))
        assertFalse(UpdateChecker.isNewer("v1.1", "1.1"))
    }

    @Test
    fun `missing trailing segments count as zero`() {
        assertFalse(UpdateChecker.isNewer("1.0", "1"))
        assertFalse(UpdateChecker.isNewer("1", "1.0"))
        assertTrue(UpdateChecker.isNewer("1", "1.0.1"))
    }

    @Test
    fun `suffixes do not break the comparison`() {
        assertTrue(UpdateChecker.isNewer("1.0", "1.1-beta"))
        assertFalse(UpdateChecker.isNewer("1.1", "1.1-beta"))
    }

    @Test
    fun `garbage never reports an update`() {
        assertFalse(UpdateChecker.isNewer("1.0", ""))
        assertFalse(UpdateChecker.isNewer("1.0", "not-a-version"))
    }
}
