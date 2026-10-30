package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

/** The deck holds its order while an ending request fades, then settles. */
class DeckHoldTest {

    private val now = Instant.fromEpochSeconds(500)

    private fun request(id: Int, status: PaymentRequestStatus = PaymentRequestStatus.Open, endsAt: Long = 1_000) = PaymentRequest(
        id = List(16) { id.toByte() },
        creator = List<Byte>(16) { 1 },
        createdAt = Instant.fromEpochSeconds(0),
        status = status,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(ByteArray(32) { 3 }),
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(endsAt),
                shares = listOf(Share(List<Byte>(16) { 2 }, Fiat(20.0, CurrencyCode.USD), null)),
            ),
        ),
    )

    @Test
    fun `a request closing keeps the old order with the new contents`() {
        val a = request(1)
        val b = request(2)
        val closedA = request(1, PaymentRequestStatus.Closed)
        val held = holdForEndedFade(shown = listOf(a, b), shownNow = now, next = listOf(b, closedA), now = now)
        assertEquals(listOf(closedA, b), held)
    }

    @Test
    fun `a request expiring by the clock holds the order too`() {
        val a = request(1, endsAt = 600)
        val b = request(2)
        val later = Instant.fromEpochSeconds(700)
        val held = holdForEndedFade(listOf(a, b), now, listOf(b, a), later)
        assertEquals(listOf(a, b), held)
    }

    @Test
    fun `an order change with nothing ending is not held`() {
        val a = request(1)
        val b = request(2)
        assertNull(holdForEndedFade(listOf(a, b), now, listOf(b, a), now))
    }

    @Test
    fun `an unchanged order needs no hold`() {
        val a = request(1)
        val closedA = request(1, PaymentRequestStatus.Closed)
        assertNull(holdForEndedFade(listOf(a), now, listOf(closedA), now))
    }

    @Test
    fun `a different set of requests is not held`() {
        val a = request(1)
        val b = request(2)
        val c = request(3)
        assertNull(holdForEndedFade(listOf(a, b), now, listOf(c, request(1, PaymentRequestStatus.Closed)), now))
    }
}
