package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.GetPaymentIntentIdError
import com.flipcash.services.models.buildRequestedPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.CollectionAmount
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentCollection
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.LiveSecrets
import com.getcode.vendor.Base58
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class PaymentRequestPaymentDelegateTest {

    private val resolverController = mockk<ResolverController>()
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val calculator = mockk<VerifiedFiatCalculator>()
    private val exchange = mockk<Exchange>(relaxed = true)
    private val paymentRequests = mockk<PaymentRequests>()
    private val held = MutableStateFlow<Map<ID, PaymentRequest>>(emptyMap())

    private val funder = mockk<CollectionFunder>(relaxed = true)

    private val delegate = PaymentRequestPaymentDelegate(
        resolverController, transactionController, tokenCoordinator, calculator, exchange, paymentRequests, funder,
    )

    private val chatId = ChatId(ByteArray(32) { 9 })
    private val requestId: ID = List(32) { 4 }
    private val creator: ID = List(16) { 1 }
    private val viewer: ID = List(16) { 2 }
    private val creatorKey = mockk<PublicKey>()
    private val intentBytes = ByteArray(32) { 7 }
    private val token = mockk<Token>(relaxed = true)
    private val source = mockk<AccountCluster>(relaxed = true)
    private val state = mockk<VerifiedState>()
    private val eur = mockk<Rate>()

    private fun request(
        status: PaymentRequestStatus = PaymentRequestStatus.Open,
        viewerPaid: Boolean = false,
    ) = PaymentRequest(
        id = requestId,
        creator = creator,
        createdAt = Clock.System.now(),
        status = status,
        closedAt = null,
        version = 3,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.EUR,
                endsAt = Clock.System.now() + 7.days,
                shares = listOf(
                    Share(creator, Fiat("12.50", CurrencyCode.EUR), PaidShare(Clock.System.now(), PaidShare.How.Creator)),
                    Share(
                        viewer,
                        Fiat("12.50", CurrencyCode.EUR),
                        if (viewerPaid) PaidShare(Clock.System.now(), PaidShare.How.Payment) else null,
                    ),
                ),
            ),
        ),
    )

    private fun serverReturns(request: PaymentRequest, intentId: PaymentIntentId?) {
        coEvery { paymentRequests.fetch(requestId) } returns Result.success(PaymentRequestDetails(request, intentId))
    }

    init {
        every { paymentRequests.state } returns held
        every { exchange.rateFor(CurrencyCode.EUR) } returns eur
        coEvery { resolverController.resolve(userId = creator) } returns Result.success(creatorKey)
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
        coEvery { funder.fund(any(), any(), any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `an unpaid share with an issued intent id is ready to pay`() = runTest {
        serverReturns(request(), PaymentIntentId(intentBytes))

        val ready = assertIs<ShareToPay.Ready>(delegate.prepare(requestId, viewer).getOrThrow())

        assertEquals(Fiat("12.50", CurrencyCode.EUR), ready.amount)
        assertFalse(ready.toString().contains(intentBytes.toList().toString()))
    }

    @Test
    fun `no intent id for the viewer means nothing to pay (case 20)`() = runTest {
        serverReturns(request(), null)

        assertIs<ShareToPay.NothingToPay>(delegate.prepare(requestId, viewer).getOrThrow())
    }

    @Test
    fun `a share paid meanwhile is nothing to pay, so a retry stops`() = runTest {
        serverReturns(request(viewerPaid = true), PaymentIntentId(intentBytes))

        assertIs<ShareToPay.NothingToPay>(delegate.prepare(requestId, viewer).getOrThrow())
    }

    @Test
    fun `a request no longer open is closed`() = runTest {
        serverReturns(request(status = PaymentRequestStatus.Closed), PaymentIntentId(intentBytes))

        assertIs<ShareToPay.Closed>(delegate.prepare(requestId, viewer).getOrThrow())
    }

    @Test
    fun `paying sends the share to the creator under the issued id, priced from the request currency`() = runTest {
        val r = request()
        serverReturns(r, PaymentIntentId(intentBytes))
        val ready = delegate.prepare(requestId, viewer).getOrThrow() as ShareToPay.Ready
        val amount = slot<Fiat>()
        val rate = slot<Rate>()
        coEvery { calculator.compute(capture(amount), any(), any(), capture(rate), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
        val metadata = slot<ByteArray>()
        val intentId = slot<PublicKey>()
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, capture(metadata), any(), capture(intentId))
        } returns Result.success(mockk())

        assertTrue(delegate.pay(ready, token, source).isSuccess)

        assertEquals(Fiat("12.50", CurrencyCode.EUR), amount.captured)
        assertSame(eur, rate.captured)
        assertContentEquals(intentBytes, intentId.captured.byteArray)
        assertContentEquals(buildRequestedPaymentMetadata(chatId, requestId.toByteArray()), metadata.captured)
        coVerify(exactly = 2) { paymentRequests.fetch(requestId) }
    }

    private suspend fun payWith(outcome: Result<Unit>): List<String> {
        serverReturns(request(), PaymentIntentId(intentBytes))
        val ready = delegate.prepare(requestId, viewer).getOrThrow() as ShareToPay.Ready
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, any(), any(), any())
        } returns outcome.map { mockk() }
        val lines = mutableListOf<String>()
        delegate.log = { message, _ -> lines += message }
        delegate.pay(ready, token, source)
        return lines
    }

    private val intentBase58 = Base58.encode(intentBytes)

    @Test
    fun `a paid share logs its intent id in full and releases it`() = runTest {
        val lines = payWith(Result.success(Unit))

        assertTrue(lines.single().contains(intentBase58), lines.single())
        assertFalse(LiveSecrets.isLive(intentBase58))
    }

    @Test
    fun `a failed payment logs only the last six characters and keeps the id masked`() = runTest {
        val lines = payWith(Result.failure(IllegalStateException("boom")))

        assertTrue(lines.single().endsWith("…${intentBase58.takeLast(6)}"), lines.single())
        assertFalse(intentBase58 in lines.single())
        assertTrue(LiveSecrets.isLive(intentBase58))
        LiveSecrets.release(intentBase58)
    }

    // A collection the viewer's screen already holds, as the chat card and Request details do.
    private fun collection(status: PaymentRequestStatus = PaymentRequestStatus.Open) = PaymentRequest(
        id = requestId,
        creator = creator,
        createdAt = Clock.System.now(),
        status = status,
        closedAt = null,
        version = 3,
        kind = PaymentRequest.Kind.GroupCollection(
            chatId = chatId,
            collection = PaymentCollection(
                text = PaymentRequestText.Plain("", ""),
                currency = CurrencyCode.EUR,
                endsAt = null,
                amount = CollectionAmount.Fixed(Fiat("10.00", CurrencyCode.EUR)),
            ),
        ),
    )

    private fun holdingCollection(intentId: Result<PaymentIntentId>) {
        held.value = mapOf(requestId to collection())
        coEvery { paymentRequests.paymentIntentId(requestId) } returns intentId
        coEvery { paymentRequests.fetch(requestId) } returns
            Result.success(PaymentRequestDetails(collection(PaymentRequestStatus.Closed), null))
        coEvery { paymentRequests.loadPayers(requestId) } returns Result.success(PaymentRequests.PaidPayers(4, emptyList()))
    }

    @Test
    fun `a collection asks GetPaymentIntentId and is ready for its fixed amount, without a refetch`() = runTest {
        holdingCollection(Result.success(PaymentIntentId(intentBytes)))

        val ready = assertIs<ShareToPay.Ready>(delegate.prepare(requestId, viewer).getOrThrow())

        assertEquals(Fiat("10.00", CurrencyCode.EUR), ready.amount)
        assertContentEquals(intentBytes, ready.intentId.bytes)
        coVerify(exactly = 0) { paymentRequests.fetch(any()) }
    }

    @Test
    fun `a collection not open refetches and is closed`() = runTest {
        holdingCollection(Result.failure(GetPaymentIntentIdError.NotOpen()))

        val closed = assertIs<ShareToPay.Closed>(delegate.prepare(requestId, viewer).getOrThrow())

        assertEquals(PaymentRequestStatus.Closed, closed.request.status)
        coVerify(exactly = 1) { paymentRequests.fetch(requestId) }
        coVerify(exactly = 1) { paymentRequests.loadPayers(requestId) }
    }

    @Test
    fun `a collection already paid, or a viewer who is no payer, refetches and has nothing to pay`() = runTest {
        for (error in listOf(GetPaymentIntentIdError.AlreadyPaid(), GetPaymentIntentIdError.NotAPayer())) {
            holdingCollection(Result.failure(error))

            assertIs<ShareToPay.NothingToPay>(delegate.prepare(requestId, viewer).getOrThrow(), error.message)
        }
        coVerify(exactly = 2) { paymentRequests.fetch(requestId) }
    }

    @Test
    fun `a collection denied or not found is unavailable`() = runTest {
        for (error in listOf(GetPaymentIntentIdError.Denied(), GetPaymentIntentIdError.NotFound())) {
            holdingCollection(Result.failure(error))

            assertIs<ShareToPay.Unavailable>(delegate.prepare(requestId, viewer).getOrThrow(), error.message)
        }
    }

    @Test
    fun `any other GetPaymentIntentId failure fails prepare`() = runTest {
        val error = GetPaymentIntentIdError.Other(IllegalStateException("offline"))
        holdingCollection(Result.failure(error))

        assertSame(error, delegate.prepare(requestId, viewer).exceptionOrNull())
    }

    @Test
    fun `a collection fetched rather than held still asks GetPaymentIntentId`() = runTest {
        coEvery { paymentRequests.fetch(requestId) } returns Result.success(PaymentRequestDetails(collection(), null))
        coEvery { paymentRequests.paymentIntentId(requestId) } returns Result.success(PaymentIntentId(intentBytes))

        val ready = assertIs<ShareToPay.Ready>(delegate.prepare(requestId, viewer).getOrThrow())

        assertEquals(Fiat("10.00", CurrencyCode.EUR), ready.amount)
    }

    @Test
    fun `a retry after an unknown outcome asks for the intent id again and stops once paid`() = runTest {
        holdingCollection(Result.success(PaymentIntentId(intentBytes)))
        assertIs<ShareToPay.Ready>(delegate.prepare(requestId, viewer).getOrThrow())

        coEvery { paymentRequests.paymentIntentId(requestId) } returns Result.failure(GetPaymentIntentIdError.AlreadyPaid())

        assertIs<ShareToPay.NothingToPay>(delegate.prepare(requestId, viewer).getOrThrow())
        coVerify(exactly = 2) { paymentRequests.paymentIntentId(requestId) }
    }

    @Test
    fun `paying a collection sends its fixed amount to the creator under the issued id, with the request in its metadata`() = runTest {
        holdingCollection(Result.success(PaymentIntentId(intentBytes)))
        val ready = delegate.prepare(requestId, viewer).getOrThrow() as ShareToPay.Ready
        val amount = slot<Fiat>()
        val rate = slot<Rate>()
        coEvery { calculator.compute(capture(amount), any(), any(), capture(rate), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
        val metadata = slot<ByteArray>()
        val intentId = slot<PublicKey>()
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, capture(metadata), any(), capture(intentId))
        } returns Result.success(mockk())

        assertTrue(delegate.pay(ready, token, source).isSuccess)

        assertEquals(Fiat("10.00", CurrencyCode.EUR), amount.captured)
        assertSame(eur, rate.captured)
        assertContentEquals(intentBytes, intentId.captured.byteArray)
        assertContentEquals(buildRequestedPaymentMetadata(chatId, requestId.toByteArray()), metadata.captured)
        coVerify(exactly = 1) { paymentRequests.fetch(requestId) }
        coVerify(exactly = 1) { paymentRequests.loadPayers(requestId) }
    }

    private suspend fun payCollectionWith(outcome: Result<Unit>): Pair<List<String>, Boolean> {
        holdingCollection(Result.success(PaymentIntentId(intentBytes)))
        val ready = delegate.prepare(requestId, viewer).getOrThrow() as ShareToPay.Ready
        var heldWhileSending = false
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, any(), any(), any())
        } answers {
            heldWhileSending = LiveSecrets.isLive(intentBase58)
            outcome.map { mockk() }
        }
        val lines = mutableListOf<String>()
        delegate.log = { message, _ -> lines += message }
        delegate.pay(ready, token, source)
        return lines to heldWhileSending
    }

    @Test
    fun `a collection's intent id reaches no log line when the payment lands`() = runTest {
        val (lines, heldWhileSending) = payCollectionWith(Result.success(Unit))

        assertTrue(lines.isNotEmpty())
        lines.forEach { assertFalse(intentBase58.takeLast(6) in it, it) }
        assertTrue(heldWhileSending, "lower layers must see the id masked while it is sent")
        assertTrue(LiveSecrets.isLive(intentBase58), "the call logs its bodies after it resumes")
        LiveSecrets.release(intentBase58)
    }

    @Test
    fun `a collection's intent id reaches no log line when the payment fails`() = runTest {
        val (lines, heldWhileSending) = payCollectionWith(Result.failure(IllegalStateException("boom")))

        assertTrue(lines.isNotEmpty())
        lines.forEach { assertFalse(intentBase58.takeLast(6) in it, it) }
        assertTrue(heldWhileSending)
        assertTrue(LiveSecrets.isLive(intentBase58), "an unspent id stays masked after a failure")
        LiveSecrets.release(intentBase58)
    }

    private suspend fun readyCollection(): ShareToPay.Ready {
        holdingCollection(Result.success(PaymentIntentId(intentBytes)))
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, any(), any(), any())
        } returns Result.success(mockk())
        return delegate.prepare(requestId, viewer).getOrThrow() as ShareToPay.Ready
    }

    @Test
    fun `without funding a collection pays from the balance as it stands`() = runTest {
        val ready = readyCollection()

        assertTrue(delegate.pay(ready, token, source).isSuccess)

        coVerify(exactly = 0) { funder.fund(any(), any(), any(), any()) }
        coVerify(exactly = 0) { funder.paid(any(), any()) }
    }

    @Test
    fun `settling a collection's kept conversions prices them in the request's currency`() = runTest {
        val ready = readyCollection()
        coEvery { funder.settle(any(), any(), any()) } returns Result.success(true)

        assertEquals(true, delegate.settleKeptConversions(ready, source).getOrThrow())

        coVerify(exactly = 1) { funder.settle(requestId, CurrencyCode.EUR, source) }
    }

    @Test
    fun `with funding a collection converts first, then pays once, then records the currency`() = runTest {
        val ready = readyCollection()

        assertTrue(delegate.pay(ready, token, source, fund = true).isSuccess)

        coVerifyOrder {
            funder.fund(requestId, Fiat("10.00", CurrencyCode.EUR), token, source)
            transactionController.directTransfer(any(), token, source, creatorKey, any(), any(), any())
            funder.paid(requestId, token)
        }
        coVerify(exactly = 1) { transactionController.directTransfer(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `funding that stops short stops the payment`() = runTest {
        val ready = readyCollection()
        val short = CollectionFundingError.Short(mockk())
        coEvery { funder.fund(any(), any(), any(), any()) } returns Result.failure(short)

        assertSame(short, delegate.pay(ready, token, source, fund = true).exceptionOrNull())

        coVerify(exactly = 0) { transactionController.directTransfer(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { funder.paid(any(), any()) }
    }

    @Test
    fun `a failed payment after funding keeps the currency unrecorded`() = runTest {
        val ready = readyCollection()
        coEvery {
            transactionController.directTransfer(any(), token, source, creatorKey, any(), any(), any())
        } returns Result.failure(IllegalStateException("boom"))

        assertTrue(delegate.pay(ready, token, source, fund = true).isFailure)

        coVerify(exactly = 0) { funder.paid(any(), any()) }
    }

    @Test
    fun `a retry that finds the collection already paid stops and forgets the kept conversions`() = runTest {
        holdingCollection(Result.failure(GetPaymentIntentIdError.AlreadyPaid()))

        assertIs<ShareToPay.NothingToPay>(delegate.prepare(requestId, viewer).getOrThrow())

        coVerify(exactly = 1) { funder.forget(requestId) }
        coVerify(exactly = 0) { funder.fund(any(), any(), any(), any()) }
        coVerify(exactly = 0) { transactionController.directTransfer(any(), any(), any(), any(), any(), any(), any()) }
    }
}
