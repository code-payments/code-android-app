package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.PayerSummary
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.services.models.paymentrequest.collectionPaid
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.AndroidResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** A collection's Request details under Group Money: which screen, its header, and when Pay shows. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CollectionDetailsTest {

    private val resources = AndroidResources(RuntimeEnvironment.getApplication())
    private val creator = List<Byte>(16) { 1 }
    private val viewer = List<Byte>(16) { 2 }
    private val other = List<Byte>(16) { 3 }
    private val now = Instant.fromEpochSeconds(500)
    private val ten = Fiat(10.0, CurrencyCode.USD)

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD)

    private fun collection(
        status: PaymentRequestStatus = PaymentRequestStatus.Open,
        each: Fiat = ten,
        paidQuarks: Long = 0,
    ) = PaymentRequest(
        id = List(16) { 9 },
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = status,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = ChatId(ByteArray(32) { 3 }),
            collection = PaymentCollection(PaymentRequestText.Plain("", ""), CurrencyCode.USD, null, CollectionAmount.Fixed(each)),
        ),
        payerSummary = PayerSummary(payerCount = 0, paidCount = 0, amountDueQuarks = 0, amountPaidQuarks = paidQuarks),
    )

    private fun split() = PaymentRequest(
        id = List(16) { 8 },
        creator = creator,
        createdAt = Instant.fromEpochSeconds(0),
        status = PaymentRequestStatus.Open,
        closedAt = null,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = ChatId(ByteArray(32) { 3 }),
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = Instant.fromEpochSeconds(1_000),
                shares = listOf(Share(creator, ten, null), Share(viewer, ten, null)),
            ),
        ),
    )

    private fun paid(userId: List<Byte>) = Payer(
        userId = userId,
        amountDueQuarks = 0,
        amountPaidQuarks = ten.quarks,
        status = Payer.Status.Paid(PaidShare(Instant.fromEpochSeconds(10), PaidShare.How.Payment)),
    )

    // Spec test case: a split with the flag on still shows the split screen.
    @Test
    fun `a split keeps the shipped details with Group Money on, and a collection needs the flag`() {
        assertNull(collectionDetailsOf(split(), collections = true))
        assertNull(collectionDetailsOf(collection(), collections = false))
        val held = collection()
        assertSame(held, collectionDetailsOf(held, collections = true))
    }

    // Spec test case: the header copy.
    @Test
    fun `the header reads what came in and what each member pays`() {
        assertEquals("\$0 paid · \$10.00 each", collectionHeaderText(resources, usd(0.0), ten))
        assertEquals("\$22.00 paid · \$11.00 each", collectionHeaderText(resources, usd(22.0), usd(11.0)))
    }

    @Test
    fun `the header's total is the payer summary's, which starts at zero`() {
        assertEquals(usd(0.0), collection().collectionPaid)
        assertEquals(usd(22.0), collection(each = usd(11.0), paidQuarks = usd(22.0).quarks).collectionPaid)
    }

    // Spec test case: Pay shows only while OPEN, for a non-creator, who has not paid.
    @Test
    fun `a member who has not paid an open collection sees Pay for the fixed amount`() {
        assertEquals(ten, payableCollectionAmount(collection(), viewer, now, callerPaid = false, paid = emptyList()))
        assertEquals(ten, payableCollectionAmount(collection(), viewer, now, callerPaid = false, paid = listOf(paid(other))))
    }

    @Test
    fun `no Pay for the creator, a closed collection, or a viewer who has paid`() {
        assertNull(payableCollectionAmount(collection(), creator, now, callerPaid = false, paid = emptyList()))
        assertNull(
            payableCollectionAmount(collection(PaymentRequestStatus.Closed), viewer, now, callerPaid = false, paid = emptyList()),
        )
        assertNull(payableCollectionAmount(collection(), viewer, now, callerPaid = true, paid = emptyList()))
        assertNull(payableCollectionAmount(collection(), viewer, now, callerPaid = false, paid = listOf(paid(viewer))))
        assertNull(payableCollectionAmount(collection(), null, now, callerPaid = false, paid = emptyList()))
    }

    @Test
    fun `a fetch that started before the payment landed keeps the viewer paid`() {
        assertEquals(true, callerPaidAfterFetch(heldPaid = true, fetchedPaid = false))
        assertEquals(true, callerPaidAfterFetch(heldPaid = false, fetchedPaid = true))
        assertEquals(false, callerPaidAfterFetch(heldPaid = false, fetchedPaid = false))
    }

    @Test
    fun `no Pay once Pay found nothing to pay, and none for a split`() {
        assertNull(payableCollectionAmount(collection(), viewer, now, callerPaid = false, paid = emptyList(), nothingToPay = true))
        assertNull(payableCollectionAmount(split(), viewer, now, callerPaid = false, paid = emptyList()))
    }
}
