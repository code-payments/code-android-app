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

/** A completed request stays in its slot while its ring fills, then leaves; other endings leave at once. */
class DeckHoldTest {

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

    // 3, 58
    @Test
    fun `a completed front request keeps its slot until the settle`() {
        val a = request(1)
        val b = request(2)
        val completedA = request(1, PaymentRequestStatus.Completed)
        val held = holdForCompletion(shown = listOf(a, b), next = listOf(b), held = listOf(completedA, b))
        assertEquals(listOf(completedA, b), held)
    }

    @Test
    fun `the only request completing holds a deck of one`() {
        val a = request(1)
        val completedA = request(1, PaymentRequestStatus.Completed)
        assertEquals(listOf(completedA), holdForCompletion(listOf(a), emptyList(), listOf(completedA)))
    }

    @Test
    fun `a closed request leaves at once`() {
        val a = request(1)
        val b = request(2)
        assertNull(holdForCompletion(listOf(a, b), listOf(b), listOf(request(1, PaymentRequestStatus.Closed), b)))
    }

    // 9, 60
    @Test
    fun `an expired request leaves at once`() {
        val a = request(1, endsAt = 600)
        val b = request(2)
        assertNull(holdForCompletion(listOf(a, b), listOf(b), listOf(a, b)))
    }

    @Test
    fun `nothing leaving needs no hold`() {
        val a = request(1)
        val b = request(2)
        assertNull(holdForCompletion(listOf(a, b), listOf(b, a), listOf(a, b)))
    }

    @Test
    fun `a request completed before it was shown is not brought in`() {
        val a = request(1)
        val b = request(2)
        assertNull(holdForCompletion(listOf(a), listOf(a), listOf(a, request(2, PaymentRequestStatus.Completed))))
        assertNull(holdForCompletion(emptyList(), listOf(b), listOf(b)))
    }

    @Test
    fun `a request arriving during the hold joins at the back`() {
        val a = request(1)
        val b = request(2)
        val c = request(3)
        val completedA = request(1, PaymentRequestStatus.Completed)
        assertEquals(listOf(completedA, b, c), holdForCompletion(listOf(a, b), listOf(c, b), listOf(completedA, b, c)))
    }
}
