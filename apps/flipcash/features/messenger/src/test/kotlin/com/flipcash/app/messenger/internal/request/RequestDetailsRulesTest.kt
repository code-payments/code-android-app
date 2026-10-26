package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** Who can mark a share paid and who sees "Send <share>" on Request details. */
class RequestDetailsRulesTest {

    private val creator = List<Byte>(16) { 1 }
    private val debtor = List<Byte>(16) { 2 }
    private val payer = List<Byte>(16) { 3 }
    private val now = Instant.fromEpochSeconds(500)
    private val twenty = Fiat(20.0, CurrencyCode.USD)

    private fun request(status: PaymentRequestStatus = PaymentRequestStatus.Open, endsAt: Long = 1_000) = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
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
                shares = listOf(
                    Share(creator, twenty, PaidShare(Instant.fromEpochSeconds(0), PaidShare.How.Creator)),
                    Share(debtor, twenty, null),
                    Share(payer, twenty, PaidShare(Instant.fromEpochSeconds(10), PaidShare.How.Payment)),
                ),
            ),
        ),
    )

    @Test
    fun `the creator can mark another member's unpaid share on an open request`() {
        assertTrue(canMarkPaid(request(), debtor, viewerId = creator, now = now))
    }

    @Test
    fun `paid rows and the creator's own row do nothing`() {
        assertFalse(canMarkPaid(request(), payer, creator, now))
        assertFalse(canMarkPaid(request(), creator, creator, now))
    }

    @Test
    fun `a member who did not create the request cannot mark shares`() {
        assertFalse(canMarkPaid(request(), debtor, viewerId = payer, now = now))
    }

    @Test
    fun `nothing can be marked once the request has ended`() {
        assertFalse(canMarkPaid(request(PaymentRequestStatus.Closed), debtor, creator, now))
        assertFalse(canMarkPaid(request(endsAt = 400), debtor, creator, now))
    }

    @Test
    fun `a debtor with an unpaid share on an open request sees Send`() {
        assertEquals(twenty, unpaidShareOf(request(), debtor, now)?.amount)
    }

    @Test
    fun `no Send for a paid share, a non-member, or an ended request`() {
        assertNull(unpaidShareOf(request(), payer, now))
        assertNull(unpaidShareOf(request(), List(16) { 7 }, now))
        assertNull(unpaidShareOf(request(PaymentRequestStatus.Expired), debtor, now))
        assertNull(unpaidShareOf(request(endsAt = 400), debtor, now))
    }

    @Test
    fun `case 20 - Send is hidden when the viewer has no payment intent id`() {
        assertNull(sendableShareOf(request(), debtor, now, hasPaymentIntent = false))
        assertEquals(twenty, sendableShareOf(request(), debtor, now, hasPaymentIntent = true)?.amount)
    }
}
