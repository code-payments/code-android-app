package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.paymentrequest.v1.PaymentrequestService
import com.flipcash.services.internal.network.api.PaymentRequestApi
import com.flipcash.services.models.CreatePaymentRequestError
import com.flipcash.services.models.GetPayersError
import com.flipcash.services.models.GetPaymentIntentIdError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.paymentrequest.NewCollectionRequest
import com.flipcash.services.models.paymentrequest.NewShare
import com.flipcash.services.models.paymentrequest.NewSplitRequest
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.PayerSection
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import io.mockk.coEvery
import io.mockk.slot
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.codeinc.flipcash.gen.paymentrequest.v1.Model as PaymentRequestModel

class PaymentRequestServiceTest {

    private val api = mockk<PaymentRequestApi>()
    private val service = PaymentRequestService(api)
    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)
    private val requestId = List<Byte>(16) { 9 }
    private val alice = List<Byte>(16) { 2 }
    private val intentBytes = ByteArray(32) { 4 }

    // region createPaymentRequest

    @Test
    fun `createPaymentRequest INVALID_AMOUNT returns InvalidAmount`() = runTest {
        coEvery { api.createPaymentRequest(any(), any(), any()) } returns
            PaymentrequestService.CreatePaymentRequestResponse.newBuilder()
                .setResult(PaymentrequestService.CreatePaymentRequestResponse.Result.INVALID_AMOUNT)
                .build()

        val result = service.createPaymentRequest(owner, newSplit(), IdempotencyKey(ByteArray(16)))

        assertIs<CreatePaymentRequestError.InvalidAmount>(result.exceptionOrNull())
    }

    @Test
    fun `createPaymentRequest for a collection sends the fixed amount and currency, with no text or deadline`() = runTest {
        val sent = slot<PaymentRequestModel.GroupChatPaymentRequest>()
        coEvery { api.createPaymentRequest(any(), capture(sent), any()) } returns
            PaymentrequestService.CreatePaymentRequestResponse.newBuilder()
                .setResult(PaymentrequestService.CreatePaymentRequestResponse.Result.DENIED)
                .build()
        val chatId = ChatId(ByteArray(32) { 3 })

        service.createPaymentRequest(
            owner,
            NewCollectionRequest(chatId, Fiat(quarks = 10_000_000, currencyCode = CurrencyCode.CAD)),
            IdempotencyKey(ByteArray(16)),
        )

        assertContentEquals(chatId.bytes, sent.captured.chatId.value.toByteArray())
        assertEquals(PaymentRequestModel.GroupChatPaymentRequest.TypeCase.COLLECTION, sent.captured.typeCase)
        val collection = sent.captured.collection
        assertEquals("cad", collection.currency)
        assertTrue(collection.hasFixed())
        assertEquals(10.0, collection.fixed.nativeAmount)
        assertEquals(PaymentRequestModel.Collection.TextCase.TEXT_NOT_SET, collection.textCase)
        assertFalse(collection.hasEndsAt())
    }

    @Test
    fun `createPaymentRequest for a split still sends a split`() = runTest {
        val sent = slot<PaymentRequestModel.GroupChatPaymentRequest>()
        coEvery { api.createPaymentRequest(any(), capture(sent), any()) } returns
            PaymentrequestService.CreatePaymentRequestResponse.newBuilder()
                .setResult(PaymentrequestService.CreatePaymentRequestResponse.Result.DENIED)
                .build()

        service.createPaymentRequest(owner, newSplit(), IdempotencyKey(ByteArray(16)))

        assertEquals(PaymentRequestModel.GroupChatPaymentRequest.TypeCase.SPLIT, sent.captured.typeCase)
    }

    // endregion

    // region getPaymentRequest

    @Test
    fun `getPaymentRequest maps the caller's payer`() = runTest {
        coEvery { api.getPaymentRequest(any(), any()) } returns getPaymentRequestResponse {
            setCallerPayer(unpaidPayer())
        }

        val details = service.getPaymentRequest(owner, requestId).getOrThrow()

        assertEquals(alice, details.callerPayer?.userId)
        assertEquals(Payer.Status.Unpaid, details.callerPayer?.status)
    }

    @Test
    fun `getPaymentRequest without a caller payer has none`() = runTest {
        coEvery { api.getPaymentRequest(any(), any()) } returns getPaymentRequestResponse {}

        val details = service.getPaymentRequest(owner, requestId).getOrThrow()

        assertNull(details.callerPayer)
    }

    // endregion

    // region getPaymentIntentId

    @Test
    fun `getPaymentIntentId OK returns the intent id`() = runTest {
        coEvery { api.getPaymentIntentId(any(), any()) } returns getPaymentIntentIdResponse(
            PaymentrequestService.GetPaymentIntentIdResponse.Result.OK,
        ) { setPaymentIntentId(Common.IntentId.newBuilder().setValue(ByteString.copyFrom(intentBytes))) }

        val intentId = service.getPaymentIntentId(owner, requestId).getOrThrow()

        assertContentEquals(intentBytes, intentId.bytes)
    }

    @Test
    fun `getPaymentIntentId OK without an intent id returns Other`() = runTest {
        coEvery { api.getPaymentIntentId(any(), any()) } returns
            getPaymentIntentIdResponse(PaymentrequestService.GetPaymentIntentIdResponse.Result.OK)

        val result = service.getPaymentIntentId(owner, requestId)

        assertIs<GetPaymentIntentIdError.Other>(result.exceptionOrNull())
    }

    @Test
    fun `getPaymentIntentId maps each refusal to its error`() = runTest {
        val cases = mapOf(
            PaymentrequestService.GetPaymentIntentIdResponse.Result.DENIED to GetPaymentIntentIdError.Denied::class,
            PaymentrequestService.GetPaymentIntentIdResponse.Result.NOT_FOUND to GetPaymentIntentIdError.NotFound::class,
            PaymentrequestService.GetPaymentIntentIdResponse.Result.NOT_OPEN to GetPaymentIntentIdError.NotOpen::class,
            PaymentrequestService.GetPaymentIntentIdResponse.Result.NOT_A_PAYER to GetPaymentIntentIdError.NotAPayer::class,
            PaymentrequestService.GetPaymentIntentIdResponse.Result.ALREADY_PAID to GetPaymentIntentIdError.AlreadyPaid::class,
        )
        for ((wire, expected) in cases) {
            coEvery { api.getPaymentIntentId(any(), any()) } returns getPaymentIntentIdResponse(wire)

            val error = service.getPaymentIntentId(owner, requestId).exceptionOrNull()

            assertEquals(expected, error?.let { it::class }, "for $wire")
        }
    }

    @Test
    fun `getPaymentIntentId unknown result returns Unrecognized`() = runTest {
        coEvery { api.getPaymentIntentId(any(), any()) } returns
            PaymentrequestService.GetPaymentIntentIdResponse.newBuilder().setResultValue(99).build()

        val result = service.getPaymentIntentId(owner, requestId)

        assertIs<GetPaymentIntentIdError.Unrecognized>(result.exceptionOrNull())
    }

    // endregion

    // region getPayers

    @Test
    fun `getPayers OK maps the page`() = runTest {
        val token = ByteArray(8) { 5 }
        coEvery { api.getPayers(any(), any(), any(), any()) } returns
            PaymentrequestService.GetPayersResponse.newBuilder()
                .setResult(PaymentrequestService.GetPayersResponse.Result.OK)
                .addPayers(unpaidPayer())
                .setPagingToken(Common.PagingToken.newBuilder().setValue(ByteString.copyFrom(token)))
                .setHasMore(true)
                .setVersion(7)
                .build()

        val page = service.getPayers(owner, requestId, PayerSection.UNPAID, QueryOptions()).getOrThrow()

        assertEquals(listOf(alice), page.payers.map { it.userId })
        assertEquals(token.toList(), page.pagingToken)
        assertEquals(true, page.hasMore)
        assertEquals(7, page.version)
    }

    @Test
    fun `getPayers last page has no paging token`() = runTest {
        coEvery { api.getPayers(any(), any(), any(), any()) } returns
            PaymentrequestService.GetPayersResponse.newBuilder()
                .setResult(PaymentrequestService.GetPayersResponse.Result.OK)
                .build()

        val page = service.getPayers(owner, requestId, PayerSection.PAID, QueryOptions()).getOrThrow()

        assertEquals(emptyList(), page.payers)
        assertNull(page.pagingToken)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `getPayers DENIED and NOT_FOUND return their errors`() = runTest {
        coEvery { api.getPayers(any(), any(), any(), any()) } returns getPayersResponse(
            PaymentrequestService.GetPayersResponse.Result.DENIED,
        )
        assertIs<GetPayersError.Denied>(
            service.getPayers(owner, requestId, PayerSection.UNPAID, QueryOptions()).exceptionOrNull(),
        )

        coEvery { api.getPayers(any(), any(), any(), any()) } returns getPayersResponse(
            PaymentrequestService.GetPayersResponse.Result.NOT_FOUND,
        )
        assertIs<GetPayersError.NotFound>(
            service.getPayers(owner, requestId, PayerSection.UNPAID, QueryOptions()).exceptionOrNull(),
        )
    }

    // endregion

    // region helpers

    private fun newSplit() = NewSplitRequest(
        chatId = ChatId(ByteArray(32) { 3 }),
        text = PaymentRequestText.Encrypted(scheme = 1, nonce = ByteArray(24), ciphertext = ByteArray(8)),
        currency = CurrencyCode.USD,
        endsAt = Instant.fromEpochSeconds(2_000),
        shares = listOf(NewShare(alice, Fiat(quarks = 1_000_000, currencyCode = CurrencyCode.USD))),
    )

    private fun unpaidPayer() = PaymentRequestModel.Payer.newBuilder()
        .setUserId(Common.UserId.newBuilder().setValue(ByteString.copyFrom(alice.toByteArray())))
        .setNativeAmountDue(2.5)
        .setUnpaid(PaymentRequestModel.Payer.Unpaid.getDefaultInstance())
        .build()

    private fun getPaymentRequestResponse(
        configure: PaymentrequestService.GetPaymentRequestResponse.Builder.() -> Unit,
    ): PaymentrequestService.GetPaymentRequestResponse =
        PaymentrequestService.GetPaymentRequestResponse.newBuilder()
            .setResult(PaymentrequestService.GetPaymentRequestResponse.Result.OK)
            .setPaymentRequest(
                PaymentRequestModel.PaymentRequestMetadata.newBuilder()
                    .setPaymentRequestId(
                        PaymentRequestModel.PaymentRequestId.newBuilder()
                            .setValue(ByteString.copyFrom(requestId.toByteArray())),
                    )
                    .setCreator(Common.UserId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { 1 })))
                    .setCreatedAt(Timestamp.newBuilder().setSeconds(100))
                    .setStatus(PaymentRequestModel.PaymentRequestStatus.PAYMENT_REQUEST_STATUS_OPEN)
                    .setVersion(1),
            )
            .apply(configure)
            .build()

    private fun getPaymentIntentIdResponse(
        result: PaymentrequestService.GetPaymentIntentIdResponse.Result,
        configure: PaymentrequestService.GetPaymentIntentIdResponse.Builder.() -> Unit = {},
    ): PaymentrequestService.GetPaymentIntentIdResponse =
        PaymentrequestService.GetPaymentIntentIdResponse.newBuilder()
            .setResult(result)
            .apply(configure)
            .build()

    private fun getPayersResponse(
        result: PaymentrequestService.GetPayersResponse.Result,
    ): PaymentrequestService.GetPayersResponse =
        PaymentrequestService.GetPayersResponse.newBuilder().setResult(result).build()

    // endregion
}
