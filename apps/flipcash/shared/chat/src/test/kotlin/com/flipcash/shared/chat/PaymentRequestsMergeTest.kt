package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The store keeps one copy per request id: the newer, and an opened text over a sealed one. */
class PaymentRequestsMergeTest {

    private val id = List<Byte>(16) { 9 }

    private fun request(version: Long, text: PaymentRequestText, status: PaymentRequestStatus = PaymentRequestStatus.Open) =
        PaymentRequest(
            id = id,
            creator = List(16) { 1 },
            createdAt = Instant.fromEpochSeconds(0),
            status = status,
            closedAt = null,
            version = version,
            kind = PaymentRequest.Kind.GroupSplit(
                chatId = ChatId(ByteArray(32) { 3 }),
                split = Split(text, CurrencyCode.USD, Instant.fromEpochSeconds(1_000), emptyList()),
            ),
        )

    private val plain = PaymentRequestText.Plain("Dinner", "")
    private val sealed = PaymentRequestText.Encrypted(1, ByteArray(24), ByteArray(17))

    @Test
    fun `a newer version replaces the held copy`() {
        val held = mapOf(id to request(4, plain))
        val incoming = request(5, plain, PaymentRequestStatus.Closed)
        assertEquals(incoming, PaymentRequests.merge(held, incoming)[id])
    }

    @Test
    fun `an older version is dropped`() {
        val held = mapOf(id to request(5, plain))
        assertEquals(held, PaymentRequests.merge(held, request(4, plain)))
    }

    @Test
    fun `a still-sealed copy does not replace an opened one of the same version`() {
        val held = mapOf(id to request(5, plain))
        assertEquals(held, PaymentRequests.merge(held, request(5, sealed)))
    }

    @Test
    fun `a still-sealed newer version replaces an opened older one`() {
        val held = mapOf(id to request(4, plain))
        val incoming = request(5, sealed)
        assertEquals(incoming, PaymentRequests.merge(held, incoming)[id])
    }

    // 11
    @Test
    fun `an expired copy of the same version replaces an open one`() {
        val held = mapOf(id to request(5, plain))
        val incoming = request(5, plain, PaymentRequestStatus.Expired)
        assertEquals(PaymentRequestStatus.Expired, PaymentRequests.merge(held, incoming)[id]?.status)
    }

    // 12
    @Test
    fun `an open copy of the same version does not replace an expired one`() {
        val held = mapOf(id to request(5, plain, PaymentRequestStatus.Expired))
        assertEquals(PaymentRequestStatus.Expired, PaymentRequests.merge(held, request(5, plain))[id]?.status)
    }
}
