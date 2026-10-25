package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildRequestedPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    private val delegate = PaymentRequestPaymentDelegate(
        resolverController, transactionController, tokenCoordinator, calculator, exchange, paymentRequests,
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
        every { exchange.rateFor(CurrencyCode.EUR) } returns eur
        coEvery { resolverController.resolve(userId = creator) } returns Result.success(creatorKey)
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
    }

    @Test
    fun `an unpaid share with an issued intent id is ready to pay`() = runTest {
        serverReturns(request(), PaymentIntentId(intentBytes))

        val ready = assertIs<ShareToPay.Ready>(delegate.prepare(requestId, viewer).getOrThrow())

        assertEquals(viewer, ready.share.userId)
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
}
