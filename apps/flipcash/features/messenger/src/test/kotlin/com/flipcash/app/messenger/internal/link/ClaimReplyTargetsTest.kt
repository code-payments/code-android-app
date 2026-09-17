package com.flipcash.app.messenger.internal.link

import com.flipcash.app.session.SettledClaim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaimReplyTargetsTest {

    private companion object {
        const val ENTROPY = "KNi8pQr1n5hRU65vKJGge3"
    }

    private val targets = ClaimReplyTargets()

    @Test
    fun `a tapped voucher that is collected answers with the message it came on`() {
        targets.note(ENTROPY, messageId = 42L)
        targets.tapped(ENTROPY)

        assertEquals(42L, targets.settled(SettledClaim(ENTROPY, collected = true)))
    }

    @Test
    fun `a voucher the transcript never drew is not replied to`() {
        // The reader's own voucher takes this path: the mapping skips their own messages, so there
        // is no message to pair the claim with and nobody to thank.
        targets.tapped(ENTROPY)

        assertNull(targets.settled(SettledClaim(ENTROPY, collected = true)))
    }

    @Test
    fun `a link claimed without being tapped here is not replied to`() {
        // Drawn in this chat, but opened from somewhere else -- pasted, scanned, forwarded on. The
        // claim still arrives, because it is the same entropy; the reply must not.
        targets.note(ENTROPY, messageId = 42L)

        assertNull(targets.settled(SettledClaim(ENTROPY, collected = true)))
    }

    @Test
    fun `a claim that failed is not replied to`() {
        // Every failure lands here, which is what covers the two that matter: collecting back your
        // own link, and a link someone else already took.
        targets.note(ENTROPY, messageId = 42L)
        targets.tapped(ENTROPY)

        assertNull(targets.settled(SettledClaim(ENTROPY, collected = false)))
    }

    @Test
    fun `a settled claim is only replied to once`() {
        targets.note(ENTROPY, messageId = 42L)
        targets.tapped(ENTROPY)

        assertEquals(42L, targets.settled(SettledClaim(ENTROPY, collected = true)))
        // The bill can be re-opened and the same entropy claimed again; the thank-you was already
        // said.
        assertNull(targets.settled(SettledClaim(ENTROPY, collected = true)))
    }

    @Test
    fun `a failed claim can be tapped again`() {
        targets.note(ENTROPY, messageId = 42L)
        targets.tapped(ENTROPY)
        assertNull(targets.settled(SettledClaim(ENTROPY, collected = false)))

        // Offline the first time, through the second. The voucher is still noted, so the retry
        // needs nothing from the transcript.
        targets.tapped(ENTROPY)
        assertEquals(42L, targets.settled(SettledClaim(ENTROPY, collected = true)))
    }

    @Test
    fun `each voucher answers with its own message`() {
        val other = "8mXeQ2vTb4pLzRw9dKcHfA"
        targets.note(ENTROPY, messageId = 42L)
        targets.note(other, messageId = 99L)
        targets.tapped(ENTROPY)
        targets.tapped(other)

        assertEquals(99L, targets.settled(SettledClaim(other, collected = true)))
        assertEquals(42L, targets.settled(SettledClaim(ENTROPY, collected = true)))
    }
}
