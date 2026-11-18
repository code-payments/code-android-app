package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.paymentrequest.v1.Model as PaymentRequestModel
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.PayerSummary
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.flipcash.services.models.paymentrequest.statusLine
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** `paymentrequest.v1` wire types as the app reads them. */
class PaymentRequestMappingTest {

    private val chatBytes = ByteArray(32) { 3 }
    private val alice = List<Byte>(16) { 2 }

    private fun userId(id: List<Byte>) = Common.UserId.newBuilder().setValue(ByteString.copyFrom(id.toByteArray())).build()

    private fun ts(seconds: Long) = Timestamp.newBuilder().setSeconds(seconds).build()

    private fun groupChat() = PaymentRequestModel.GroupChatPaymentRequest.newBuilder()
        .setChatId(Common.ChatId.newBuilder().setValue(ByteString.copyFrom(chatBytes)))

    private fun metadata(
        groupChat: PaymentRequestModel.GroupChatPaymentRequest.Builder,
        payerSummary: PaymentRequestModel.PayerSummary? = null,
    ) = PaymentRequestModel.PaymentRequestMetadata.newBuilder()
        .setPaymentRequestId(PaymentRequestModel.PaymentRequestId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { 9 })))
        .setCreator(userId(List(16) { 1 }))
        .setCreatedAt(ts(100))
        .setStatus(PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_OPEN)
        .setVersion(1)
        .setGroupChat(groupChat)
        .apply { payerSummary?.let { setPayerSummary(it) } }
        .build()

    private fun collection(endsAt: Long? = 2_000, fixed: Double? = 5.0) = PaymentRequestModel.Collection.newBuilder()
        .setPlaintext(PaymentRequestModel.PaymentRequestText.newBuilder().setTitle("Gift").setDescription("For Bo"))
        .setCurrency("usd")
        .apply { endsAt?.let { setEndsAt(ts(it)) } }
        .apply { fixed?.let { setFixed(PaymentRequestModel.Collection.FixedAmount.newBuilder().setNativeAmount(it)) } }

    private fun paid(how: PaymentRequestModel.Paid.Builder.() -> Unit) =
        PaymentRequestModel.Paid.newBuilder().setPaidAt(ts(300)).apply(how).build()

    private fun payer(status: PaymentRequestModel.Payer.Builder.() -> Unit) = PaymentRequestModel.Payer.newBuilder()
        .setUserId(userId(alice))
        .setNativeAmountDue(2.5)
        .setNativeAmountPaid(7.25)
        .apply(status)
        .build()

    private fun updates(vararg update: PaymentRequestModel.PaymentRequestUpdate.Update.Builder) =
        PaymentRequestModel.PaymentRequestUpdate.newBuilder()
            .setPaymentRequestId(PaymentRequestModel.PaymentRequestId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { 9 })))
            .setUpdates(
                PaymentRequestModel.PaymentRequestUpdate.Updates.newBuilder()
                    .setVersion(2)
                    .addAllUpdates(update.map { it.build() }),
            )
            .build()
            .toPaymentRequestUpdate()

    private fun onlyChange(update: PaymentRequestUpdate): PaymentRequestUpdate.Change =
        assertIs<PaymentRequestUpdate.Kind.Updates>(update.kind).changes.single()

    @Test
    fun `a fixed collection maps with its text, amount and deadline`() {
        val request = metadata(groupChat().setCollection(collection())).toPaymentRequest()

        val kind = assertIs<PaymentRequest.Kind.GroupCollection>(request.kind)
        assertContentEquals(chatBytes, kind.chatId.bytes)
        assertEquals(PaymentRequestText.Plain("Gift", "For Bo"), kind.collection.text)
        assertEquals(CurrencyCode.USD, kind.collection.currency)
        assertEquals(CollectionAmount.Fixed(Fiat(quarks = 5_000_000, currencyCode = CurrencyCode.USD)), kind.collection.amount)
        assertEquals(Instant.fromEpochSeconds(2_000), kind.collection.endsAt)
        assertEquals(Instant.fromEpochSeconds(2_000), request.endsAt)
        assertEquals(CurrencyCode.USD, request.currency)
    }

    @Test
    fun `a collection is left out of split-only reads`() {
        val request = metadata(groupChat().setCollection(collection())).toPaymentRequest()

        assertNull(request.split)
        assertNull(request.chatId)
    }

    @Test
    fun `a collection without ends_at never expires`() {
        val request = metadata(groupChat().setCollection(collection(endsAt = null))).toPaymentRequest()
        val farFuture = Instant.fromEpochSeconds(4_000_000_000)

        assertNull(assertIs<PaymentRequest.Kind.GroupCollection>(request.kind).collection.endsAt)
        assertEquals(PaymentRequestStatus.Open, request.resolvedStatus(farFuture))
        assertNull(request.resolvedClosedAt(farFuture))
        assertNull(request.statusLine(farFuture))
    }

    @Test
    fun `a collection whose amount kind the app does not know is unsupported`() {
        val request = metadata(groupChat().setCollection(collection(fixed = null))).toPaymentRequest()

        assertEquals(PaymentRequest.Kind.Unsupported, request.kind)
    }

    @Test
    fun `a group chat request of no type the app knows is unsupported`() {
        assertEquals(PaymentRequest.Kind.Unsupported, metadata(groupChat()).toPaymentRequest().kind)
    }

    @Test
    fun `a request with no text has empty plain text`() {
        val split = PaymentRequestModel.Split.newBuilder()
            .setCurrency("usd")
            .setEndsAt(ts(2_000))
            .addShares(PaymentRequestModel.Share.newBuilder().setUserId(userId(alice)).setNativeAmount(1.0))
        val collection = collection().clearText()

        val splitText = metadata(groupChat().setSplit(split)).toPaymentRequest().split?.text
        val collectionText = assertIs<PaymentRequest.Kind.GroupCollection>(metadata(groupChat().setCollection(collection)).toPaymentRequest().kind)
            .collection.text

        assertEquals(PaymentRequestText.Plain("", ""), splitText)
        assertEquals(PaymentRequestText.Plain("", ""), collectionText)
    }

    @Test
    fun `a share paid by hand maps through the renamed Paid`() {
        val split = PaymentRequestModel.Split.newBuilder()
            .setCurrency("usd")
            .setEndsAt(ts(2_000))
            .addShares(
                PaymentRequestModel.Share.newBuilder()
                    .setUserId(userId(alice))
                    .setNativeAmount(1.0)
                    .setPaid(paid { setManual(PaymentRequestModel.Paid.Manual.getDefaultInstance()) }),
            )

        val share = metadata(groupChat().setSplit(split)).toPaymentRequest().split!!.shares.single()

        assertEquals(PaidShare(Instant.fromEpochSeconds(300), PaidShare.How.Manual), share.paid)
    }

    @Test
    fun `the payer summary maps in quarks`() {
        val summary = PaymentRequestModel.PayerSummary.newBuilder()
            .setPayerCount(5)
            .setPaidCount(3)
            .setNativeAmountDue(40.0)
            .setNativeAmountPaid(60.5)
            .build()

        val request = metadata(groupChat().setCollection(collection()), payerSummary = summary).toPaymentRequest()

        assertEquals(PayerSummary(payerCount = 5, paidCount = 3, amountDueQuarks = 40_000_000, amountPaidQuarks = 60_500_000), request.payerSummary)
        assertEquals(Fiat(quarks = 60_500_000, currencyCode = CurrencyCode.USD), request.payerSummary!!.amountPaid(request.currency!!))
    }

    @Test
    fun `a request without a payer summary has none`() {
        assertNull(metadata(groupChat().setCollection(collection())).toPaymentRequest().payerSummary)
    }

    @Test
    fun `a paid payer maps with how it paid`() {
        val mapped = payer { setPaid(paid { setIntent(Common.IntentId.getDefaultInstance()) }) }.toPayer()

        assertEquals(
            Payer(
                userId = alice,
                amountDueQuarks = 2_500_000,
                amountPaidQuarks = 7_250_000,
                status = Payer.Status.Paid(PaidShare(Instant.fromEpochSeconds(300), PaidShare.How.Payment)),
            ),
            mapped,
        )
    }

    @Test
    fun `an unpaid payer maps as unpaid`() {
        val mapped = payer { setUnpaid(PaymentRequestModel.Payer.Unpaid.getDefaultInstance()) }.toPayer()

        assertEquals(Payer.Status.Unpaid, mapped.status)
    }

    @Test
    fun `a payer whose status the app does not know maps to unknown`() {
        assertEquals(Payer.Status.Unknown, payer { }.toPayer().status)
    }

    @Test
    fun `a payer update maps to the payer as it stands`() {
        val update = updates(
            PaymentRequestModel.PaymentRequestUpdate.Update.newBuilder().setPayerUpdate(
                PaymentRequestModel.PaymentRequestUpdate.PayerUpdate.newBuilder()
                    .setPayer(payer { setUnpaid(PaymentRequestModel.Payer.Unpaid.getDefaultInstance()) }),
            ),
        )

        val change = assertIs<PaymentRequestUpdate.Change.PayerChanged>(onlyChange(update))
        assertEquals(alice, change.payer.userId)
        assertEquals(Payer.Status.Unpaid, change.payer.status)
    }

    @Test
    fun `a payer summary update maps to the summary as it stands`() {
        val update = updates(
            PaymentRequestModel.PaymentRequestUpdate.Update.newBuilder().setPayerSummaryUpdate(
                PaymentRequestModel.PaymentRequestUpdate.PayerSummaryUpdate.newBuilder().setPayerSummary(
                    PaymentRequestModel.PayerSummary.newBuilder().setPayerCount(2).setPaidCount(1).setNativeAmountPaid(5.0),
                ),
            ),
        )

        assertEquals(
            PaymentRequestUpdate.Change.PayerSummaryChanged(PayerSummary(2, 1, 0, 5_000_000)),
            onlyChange(update),
        )
    }

    @Test
    fun `a payer or payer summary update missing its payload is unknown`() {
        val update = updates(
            PaymentRequestModel.PaymentRequestUpdate.Update.newBuilder()
                .setPayerUpdate(PaymentRequestModel.PaymentRequestUpdate.PayerUpdate.getDefaultInstance()),
            PaymentRequestModel.PaymentRequestUpdate.Update.newBuilder()
                .setPayerSummaryUpdate(PaymentRequestModel.PaymentRequestUpdate.PayerSummaryUpdate.getDefaultInstance()),
        )

        assertEquals(
            listOf(PaymentRequestUpdate.Change.Unknown, PaymentRequestUpdate.Change.Unknown),
            assertIs<PaymentRequestUpdate.Kind.Updates>(update.kind).changes,
        )
    }
}
