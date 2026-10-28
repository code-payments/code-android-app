package com.flipcash.app.messenger.internal.invites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLabelsTest {

    @Test
    fun `the pill drops the scheme and elides the middle of the secret`() {
        val url = "https://app.flipcash.com/chat/invite#s=" + "A".repeat(10) + "B".repeat(30) + "wxyz"

        val shown = elideInviteUrl(url)

        assertTrue(shown.startsWith("app.flipcash.com/chat/invite#s="))
        assertTrue(shown.endsWith("…wxyz"))
        assertFalse(shown.contains("B".repeat(30)))
    }

    @Test
    fun `a short link is shown whole`() {
        assertEquals("a.com/x", elideInviteUrl("https://a.com/x"))
    }
}
