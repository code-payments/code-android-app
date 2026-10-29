package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/** A cash message that pays a request earns that request one refetch, once per message. */
class PaymentArrivalsTest {

    private val openedAt = Instant.fromEpochSeconds(1_000)
    private val after = Instant.fromEpochSeconds(1_001)
    private val before = Instant.fromEpochSeconds(999)
    private val request: ID = List(16) { 7 }

    private fun cash(id: Long, at: Instant, pays: ID? = request, intent: Byte = id.toByte()) = ChatMessage(
        messageId = id,
        senderId = null,
        content = listOf(
            MessageContent.Cash(
                intentId = List(32) { intent },
                amount = Fiat(5.0),
                mint = Mint.usdf,
                paymentRequestId = pays,
            ),
        ),
        timestamp = at,
        unreadSeq = id,
    )

    @Test
    fun `a new cash message with a request id triggers a refetch`() {
        val arrivals = PaymentArrivals(openedAt)
        assertEquals(listOf(request), arrivals.newlyPaid(listOf(cash(1, after))))
    }

    @Test
    fun `a cash message without a request id does not`() {
        val arrivals = PaymentArrivals(openedAt)
        assertEquals(emptyList<ID>(), arrivals.newlyPaid(listOf(cash(1, after, pays = null))))
    }

    @Test
    fun `a cash message older than the chat open does not`() {
        val arrivals = PaymentArrivals(openedAt)
        assertEquals(emptyList<ID>(), arrivals.newlyPaid(listOf(cash(1, before))))
    }

    @Test
    fun `the same message seen twice triggers one refetch`() {
        val arrivals = PaymentArrivals(openedAt)
        val message = cash(1, after)
        assertEquals(listOf(request), arrivals.newlyPaid(listOf(message)))
        assertEquals(emptyList<ID>(), arrivals.newlyPaid(listOf(message)))
    }

    @Test
    fun `two messages for the same request trigger two refetches`() {
        val arrivals = PaymentArrivals(openedAt)
        assertEquals(listOf(request), arrivals.newlyPaid(listOf(cash(1, after))))
        assertEquals(listOf(request), arrivals.newlyPaid(listOf(cash(1, after), cash(2, after))))
    }
}
