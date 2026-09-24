package com.flipcash.app.tipping.internal

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.IdempotencyKey
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * How long one create attempt's idempotency key lives.
 *
 * `StartChat` derives the chat's identity from the caller and the key alone, so a reused key returns
 * the chat the first call made and reports `OK`, while a fresh one makes another group. That makes
 * the key's lifetime the difference between a retry and a duplicate, and the boundary this class
 * draws is the draft: every retry of the same draft sends the same key, and an edit starts over.
 */
class GroupCreateAttemptTest {

    private fun mint(seed: Byte) = Mint(ByteArray(32) { seed }.toList())

    private fun draft(
        title: String = "Ballers",
        currency: GroupCurrency = GroupCurrency.Specific(mint(1)),
        amount: Fiat = Fiat(100),
    ) = GroupDraft(title = title, picture = null, currency = currency, amount = amount)

    /** Hands out predictable keys, so a second key is visibly a second key. */
    private class Keys {
        private var next = 0
        val attempt = GroupCreateAttempt { IdempotencyKey(ByteArray(16) { (next).toByte() }).also { next++ } }
    }

    @Test
    fun `every retry of one draft sends the key the first attempt minted`() {
        val attempt = Keys().attempt
        val draft = draft()

        val first = attempt.keyFor(draft)

        assertEquals(first, attempt.keyFor(draft))
        assertEquals(first, attempt.keyFor(draft))
        // A draft rebuilt from the same values is the same attempt — the form hands over a fresh
        // instance on every tap, so identity has to be by value or no retry would ever reuse a key.
        assertEquals(first, attempt.keyFor(draft()))
    }

    @Test
    fun `an edited draft is a new attempt, so it gets a new key`() {
        val attempt = Keys().attempt
        val first = attempt.keyFor(draft())

        assertNotEquals(first, attempt.keyFor(draft(title = "Ballers 2")))
        assertNotEquals(first, attempt.keyFor(draft(amount = Fiat(50))))
        assertNotEquals(first, attempt.keyFor(draft(currency = GroupCurrency.Specific(mint(2)))))
    }

    /**
     * Switching between All Currencies and a token changes what the server will enforce, so a key
     * minted for one must not return the chat created for the other.
     */
    @Test
    fun `any currency and one token are different attempts`() {
        val attempt = Keys().attempt
        val all = attempt.keyFor(draft(currency = GroupCurrency.All))

        assertEquals(all, attempt.keyFor(draft(currency = GroupCurrency.All)))

        val token = attempt.keyFor(draft())
        assertNotEquals(all, token)
        assertNotEquals(token, attempt.keyFor(draft(currency = GroupCurrency.All)))
    }

    @Test
    fun `a created chat ends the attempt, so the next group is a new one`() {
        val attempt = Keys().attempt
        val first = attempt.keyFor(draft())

        attempt.clear()

        assertNotEquals(first, attempt.keyFor(draft()))
    }

    @Test
    fun `an uploaded picture survives a retry and is dropped by an edit`() {
        val attempt = Keys().attempt
        val draft = draft()
        // One instance, reused: BlobId wraps a ByteArray, so two equal-looking ids are not equal.
        val blob = BlobId(ByteArray(32) { 9 })
        attempt.keyFor(draft)
        attempt.rememberPicture(blob)

        attempt.keyFor(draft)
        assertEquals(blob, attempt.picture)

        attempt.keyFor(draft(title = "Ballers 2"))
        assertNull(attempt.picture)
    }
}
