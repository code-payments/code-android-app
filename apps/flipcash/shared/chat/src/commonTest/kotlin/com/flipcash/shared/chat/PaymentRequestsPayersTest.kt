package com.flipcash.shared.chat

import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate.Change
import com.flipcash.shared.chat.PaymentRequests.PaidPayers
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A details screen's paid payers take the stream's payer updates past the list's version. */
class PaymentRequestsPayersTest {

    private val alice = List<Byte>(16) { 2 }
    private val bob = List<Byte>(16) { 3 }
    private val carol = List<Byte>(16) { 4 }

    private fun paid(user: List<Byte>, at: Long) = Payer(
        userId = user,
        amountDueQuarks = 0,
        amountPaidQuarks = 10_000_000,
        status = Payer.Status.Paid(PaidShare(Instant.fromEpochSeconds(at), PaidShare.How.Payment)),
    )

    private fun updates(version: Long, vararg changes: Change) =
        PaymentRequestUpdate.Kind.Updates(version, changes.toList())

    @Test
    fun `a new payer at the next version goes first and takes the version`() {
        val held = PaidPayers(version = 4, payers = listOf(paid(alice, at = 100)))
        val out = PaymentRequests.mergePayers(held, updates(5, Change.PayerChanged(paid(bob, at = 200))))
        assertFalse(out.gap)
        assertEquals(5, out.payers.version)
        assertEquals(listOf(bob, alice), out.payers.payers.map { it.userId })
    }

    @Test
    fun `an update at or below the list's version is already in it`() {
        val held = PaidPayers(version = 4, payers = listOf(paid(alice, at = 100)))
        val out = PaymentRequests.mergePayers(held, updates(4, Change.PayerChanged(paid(bob, at = 200))))
        assertSame(held, out.payers)
        assertFalse(out.gap)
    }

    @Test
    fun `a payer already listed is replaced, not repeated`() {
        val held = PaidPayers(version = 4, payers = listOf(paid(alice, at = 100)))
        val out = PaymentRequests.mergePayers(held, updates(5, Change.PayerChanged(paid(alice, at = 100))))
        assertEquals(listOf(alice), out.payers.payers.map { it.userId })
    }

    @Test
    fun `a payer that is not paid leaves the list`() {
        val held = PaidPayers(version = 4, payers = listOf(paid(alice, at = 100), paid(bob, at = 50)))
        val unpaid = Payer(bob, amountDueQuarks = 10_000_000, amountPaidQuarks = 0, status = Payer.Status.Unpaid)
        val out = PaymentRequests.mergePayers(held, updates(5, Change.PayerChanged(unpaid)))
        assertEquals(listOf(alice), out.payers.payers.map { it.userId })
    }

    @Test
    fun `payers paid at the same moment are ordered by user id`() {
        val held = PaidPayers(version = 1, payers = emptyList())
        val out = PaymentRequests.mergePayers(
            held,
            updates(2, Change.PayerChanged(paid(carol, at = 100)), Change.PayerChanged(paid(alice, at = 100))),
        )
        assertEquals(listOf(alice, carol), out.payers.payers.map { it.userId })
    }

    @Test
    fun `a skipped version still applies but asks for a reload`() {
        val held = PaidPayers(version = 4, payers = emptyList())
        val out = PaymentRequests.mergePayers(held, updates(7, Change.PayerChanged(paid(bob, at = 200))))
        assertTrue(out.gap)
        assertEquals(7, out.payers.version)
        assertEquals(listOf(bob), out.payers.payers.map { it.userId })
    }

    @Test
    fun `a summary change alone moves the version and keeps the payers`() {
        val held = PaidPayers(version = 4, payers = listOf(paid(alice, at = 100)))
        val out = PaymentRequests.mergePayers(held, updates(5, Change.Unknown))
        assertEquals(5, out.payers.version)
        assertEquals(listOf(alice), out.payers.payers.map { it.userId })
    }
}
