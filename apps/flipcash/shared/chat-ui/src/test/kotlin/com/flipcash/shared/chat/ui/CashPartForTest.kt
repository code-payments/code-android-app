package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which part of a cash message the viewer reads as "You received". */
class CashPartForTest {

    private val self = listOf<Byte>(1)
    private val other = listOf<Byte>(2)
    private val third = listOf<Byte>(3)

    private fun cash(
        receiver: List<Byte>? = null,
        multiReceiver: List<MessageContent.Cash.Payment> = emptyList(),
    ) = MessageContent.Cash(
        intentId = listOf(0),
        amount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.CAD),
        mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa"),
        receiver = receiver,
        tokenQuarks = 700_000L,
        multiReceiver = multiReceiver,
    )

    @Test
    fun `a group send gives the viewer their own part`() {
        val mine = MessageContent.Cash.Payment(self, 0.34, 240_000L)
        val theirs = MessageContent.Cash.Payment(other, 0.33, 230_000L)
        assertEquals(mine, cash(multiReceiver = listOf(theirs, mine)).partFor(self))
    }

    @Test
    fun `a group send that skips the viewer gives nothing`() {
        val theirs = MessageContent.Cash.Payment(other, 0.5, 1L)
        val others = MessageContent.Cash.Payment(third, 0.5, 1L)
        assertNull(cash(multiReceiver = listOf(theirs, others)).partFor(self))
    }

    @Test
    fun `a single receiver gets the whole amount`() {
        assertEquals(
            MessageContent.Cash.Payment(self, 1.0, 700_000L),
            cash(receiver = self).partFor(self),
        )
    }

    @Test
    fun `a single receiver who is someone else gives nothing`() {
        assertNull(cash(receiver = other).partFor(self))
    }

    @Test
    fun `a message naming no one gives nothing`() {
        assertNull(cash().partFor(self))
    }
}
