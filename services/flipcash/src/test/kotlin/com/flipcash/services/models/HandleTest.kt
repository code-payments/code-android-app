package com.flipcash.services.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shape rule stands in for the server's `^[a-z0-9_]{2,15}$`, and three places rely on it without
 * being able to ask: the vanity deeplink, the tip card's link row, and the entry screen's own
 * pre-submit check. Mirrors iOS `UsernameValidatorTests`.
 */
class HandleTest {

    @Test
    fun `a plain handle is username shaped`() {
        assertTrue("sally_streamer".isUsernameShaped())
    }

    @Test
    fun `the prefix is tolerated on the way in`() {
        assertTrue("@sally_streamer".isUsernameShaped())
    }

    @Test
    fun `digits and underscores are in the charset`() {
        assertTrue("a_1".isUsernameShaped())
        assertTrue("_".repeat(MinUsernameLength).isUsernameShaped())
    }

    @Test
    fun `both ends of the length range are accepted`() {
        assertTrue("a".repeat(MinUsernameLength).isUsernameShaped())
        assertTrue("a".repeat(MaxUsernameLength).isUsernameShaped())
    }

    @Test
    fun `either side of the length range is not`() {
        assertFalse("a".repeat(MinUsernameLength - 1).isUsernameShaped())
        assertFalse("a".repeat(MaxUsernameLength + 1).isUsernameShaped())
        assertFalse("".isUsernameShaped())
    }

    // Not normalized here on purpose: the entry screen lowercases as the user types, and a link
    // is lowercased before it is matched. Accepting mixed case would let `flipcash.com/Download`
    // read as a handle.
    @Test
    fun `uppercase is not username shaped`() {
        assertFalse("SallyStreamer".isUsernameShaped())
        assertFalse("sally_Streamer".isUsernameShaped())
    }

    @Test
    fun `punctuation outside the charset is rejected`() {
        assertFalse("sally.streamer".isUsernameShaped())
        assertFalse("sally-streamer".isUsernameShaped())
        assertFalse("sally streamer".isUsernameShaped())
        assertFalse("sally@streamer".isUsernameShaped())
    }

    @Test
    fun `an embedded newline can't smuggle a valid line past the check`() {
        assertFalse("sally\ndownload".isUsernameShaped())
    }

    @Test
    fun `asHandle adds the prefix`() {
        assertEquals("@sally_streamer", "sally_streamer".asHandle())
    }

    @Test
    fun `asHandle is idempotent`() {
        assertEquals("@sally_streamer", "@sally_streamer".asHandle())
        assertEquals("@sally_streamer", "sally_streamer".asHandle().asHandle())
    }

    @Test
    fun `a profile without a username has no handle`() {
        assertNull(UserProfile.Empty.copy(username = null).handle)
    }

    @Test
    fun `a blank username counts as unclaimed rather than a bare at sign`() {
        assertNull(UserProfile.Empty.copy(username = "").handle)
        assertNull(UserProfile.Empty.copy(username = "   ").handle)
    }

    @Test
    fun `a claimed username reads as a handle`() {
        assertEquals("@sally_streamer", UserProfile.Empty.copy(username = "sally_streamer").handle)
    }
}
