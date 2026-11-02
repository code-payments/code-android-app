package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate.Change
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Request updates apply by version: ignore at or below, apply at +1, refetch beyond or when unappliable. */
class PaymentRequestsUpdateTest {

    private val id = List<Byte>(16) { 9 }
    private val alice = List<Byte>(16) { 2 }
    private val bob = List<Byte>(16) { 3 }
    private val paidAt = Instant.fromEpochSeconds(500)
    private val closedAt = Instant.fromEpochSeconds(600)

    private fun share(user: List<Byte>, paid: PaidShare? = null) =
        Share(user, Fiat(quarks = 10_000_000, currencyCode = CurrencyCode.USD), paid)

    private fun request(version: Long, shares: List<Share> = listOf(share(alice), share(bob))) = PaymentRequest(
        id = id,
        creator = List(16) { 1 },
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = version,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(ByteArray(32) { 3 }),
            split = Split(PaymentRequestText.Plain("Dinner", ""), CurrencyCode.USD, Instant.fromEpochSeconds(1_000), shares),
        ),
    )

    private fun updates(version: Long, vararg changes: Change) =
        PaymentRequestUpdate(id, PaymentRequestUpdate.Kind.Updates(version, changes.toList()))

    private val paid = PaidShare(paidAt, PaidShare.How.Payment)

    private fun apply(held: PaymentRequest?, update: PaymentRequestUpdate) =
        PaymentRequests.applyUpdate(if (held == null) emptyMap() else mapOf(id to held), update)

    @Test
    fun `an update at the next version applies and sets the version`() {
        val out = apply(request(3), updates(4, Change.SharePaid(alice, paid)))
        assertFalse(out.refetch)
        val result = out.requests.getValue(id)
        assertEquals(4, result.version)
        assertEquals(paid, result.split?.shareOf(alice)?.paid)
        assertNull(result.split?.shareOf(bob)?.paid)
    }

    @Test
    fun `a paid share keeps the held amount`() {
        val held = request(3)
        val result = apply(held, updates(4, Change.SharePaid(bob, paid))).requests.getValue(id)
        assertEquals(held.split?.shareOf(bob)?.amount, result.split?.shareOf(bob)?.amount)
    }

    @Test
    fun `a payment replaces a hand mark on the same share`() {
        val manual = PaidShare(Instant.fromEpochSeconds(100), PaidShare.How.Manual)
        val held = request(3, listOf(share(alice, manual), share(bob)))
        val result = apply(held, updates(4, Change.SharePaid(alice, paid))).requests.getValue(id)
        assertEquals(paid, result.split?.shareOf(alice)?.paid)
    }

    @Test
    fun `a status change sets status and closedAt`() {
        val out = apply(request(3), updates(4, Change.StatusChanged(PaymentRequestStatus.Closed, closedAt)))
        val result = out.requests.getValue(id)
        assertEquals(PaymentRequestStatus.Closed, result.status)
        assertEquals(closedAt, result.closedAt)
        assertEquals(4, result.version)
    }

    @Test
    fun `the last share paid and completion apply together in order`() {
        val held = request(3, listOf(share(alice, paid), share(bob)))
        val out = apply(
            held,
            updates(4, Change.SharePaid(bob, paid), Change.StatusChanged(PaymentRequestStatus.Completed, closedAt)),
        )
        val result = out.requests.getValue(id)
        assertTrue(result.split!!.shares.all { it.isPaid })
        assertEquals(PaymentRequestStatus.Completed, result.status)
        assertEquals(closedAt, result.closedAt)
    }

    @Test
    fun `an update at the held version is ignored`() {
        val held = request(4)
        val out = apply(held, updates(4, Change.StatusChanged(PaymentRequestStatus.Closed, closedAt)))
        assertEquals(mapOf(id to held), out.requests)
        assertFalse(out.refetch)
    }

    @Test
    fun `an update below the held version is ignored even when it has an unknown change`() {
        val held = request(5)
        val out = apply(held, updates(3, Change.Unknown))
        assertEquals(mapOf(id to held), out.requests)
        assertFalse(out.refetch)
    }

    @Test
    fun `an update delivered twice applies once`() {
        val update = updates(4, Change.SharePaid(alice, paid))
        val once = apply(request(3), update)
        val twice = PaymentRequests.applyUpdate(once.requests, update)
        assertEquals(once.requests, twice.requests)
        assertFalse(twice.refetch)
    }

    @Test
    fun `an update past the next version leaves the copy and asks for a refetch`() {
        val held = request(3)
        val out = apply(held, updates(5, Change.SharePaid(alice, paid)))
        assertEquals(mapOf(id to held), out.requests)
        assertTrue(out.refetch)
    }

    @Test
    fun `an out-of-order update is refetched, and the earlier one then ignored`() {
        val held = request(3)
        val late = updates(5, Change.StatusChanged(PaymentRequestStatus.Closed, closedAt))
        assertTrue(apply(held, late).refetch)
        // The refetch landed at version 5; the update for 4 that shows up afterwards is stale.
        val fetched = request(5)
        assertFalse(PaymentRequests.applyUpdate(mapOf(id to fetched), updates(4, Change.SharePaid(alice, paid))).refetch)
    }

    @Test
    fun `no held copy ignores the update without a refetch`() {
        val out = apply(null, updates(4, Change.SharePaid(alice, paid)))
        assertTrue(out.requests.isEmpty())
        assertFalse(out.refetch)
    }

    @Test
    fun `an unknown change applies none of the batch and refetches`() {
        val held = request(3)
        val out = apply(held, updates(4, Change.SharePaid(alice, paid), Change.Unknown))
        assertEquals(mapOf(id to held), out.requests)
        assertTrue(out.refetch)
    }

    @Test
    fun `a share the copy does not have applies none of the batch and refetches`() {
        val held = request(3)
        val stranger = List<Byte>(16) { 7 }
        val out = apply(
            held,
            updates(4, Change.SharePaid(alice, paid), Change.SharePaid(stranger, paid)),
        )
        assertEquals(mapOf(id to held), out.requests)
        assertTrue(out.refetch)
    }

    @Test
    fun `an unknown update kind refetches a held request and ignores an absent one`() {
        val unknown = PaymentRequestUpdate(id, PaymentRequestUpdate.Kind.Unknown)
        val held = request(3)
        assertTrue(apply(held, unknown).refetch)
        assertEquals(mapOf(id to held), apply(held, unknown).requests)
        assertFalse(apply(null, unknown).refetch)
    }

    @Test
    fun `a created request merges like a fetched copy`() {
        // Created goes through merge: the greater version wins, EXPIRED over OPEN at a tie.
        val held = mapOf(id to request(5))
        assertEquals(held, PaymentRequests.merge(held, request(4)))
        assertEquals(6, PaymentRequests.merge(held, request(6))[id]?.version)
        val expired = request(5).copy(status = PaymentRequestStatus.Expired)
        assertEquals(PaymentRequestStatus.Expired, PaymentRequests.merge(mapOf(id to expired), request(5))[id]?.status)
    }
}
