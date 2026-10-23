package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.extensions.exchangeDataFor
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import io.mockk.slot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GiveBillTransactorTest {

    private val messagingController = mockk<MessagingController>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)

    private val payloadFactory = PayloadFactory { _, _, _ ->
        PayloadResult(rendezvous = mockk(relaxed = true), codeData = emptyList())
    }

    private fun createTransactor(scope: TestScope): GiveBillTransactor {
        return GiveBillTransactor(
            messagingController = messagingController,
            transactionController = transactionController,
            scope = scope,
            payloadFactory = payloadFactory,
            verifiedFiatCalculator = verifiedFiatCalculator,
        )
    }

    // region preconditions

    @Test
    fun `start fails when with() not called`() = runTest {
        val transactor = createTransactor(this)

        val result = transactor.start()

        assertTrue(result.isFailure)
    }

    @Test
    fun `start fails when no verified state provided`() = runTest {
        val transactor = createTransactor(this)
        // Pass verifiedState = null → start() uses providedVerifiedState which is null → failure
        setupWith(transactor, verifiedState = null)

        val result = transactor.start()

        assertTrue(result.isFailure)
    }

    @Test
    fun `start fails when exchange data expired`() = runTest {
        val transactor = createTransactor(this)
        // Provide verified state directly — its rate is stale
        val verifiedState = mockk<VerifiedState>(relaxed = true)
        setupWith(transactor, verifiedState = verifiedState)

        mockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
        every { verifiedState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns null

        // Fresh resolve also returns a stale state so the retry still fails
        val freshState = mockk<VerifiedState>(relaxed = true)
        every { freshState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns null
        coEvery {
            verifiedFiatCalculator.resolveVerifiedState(any<CurrencyCode>(), any<Mint>())
        } returns freshState

        val result = transactor.start()

        assertTrue(result.isFailure)
        assertIs<GiveBillTransactor.GiveTransactorError.ExchangeRateExpiredException>(result.exceptionOrNull())

        unmockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
    }

    @Test
    fun `start refreshes state when exchange data expired`() = runTest {
        val transactor = createTransactor(this)
        // Provide verified state whose rate is stale
        val staleState = mockk<VerifiedState>(relaxed = true)
        setupWith(transactor, verifiedState = staleState)

        mockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
        every { staleState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns null

        // Fresh resolve returns a valid state with fresh exchange data
        val freshState = mockk<VerifiedState>(relaxed = true)
        every { freshState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns mockk<ExchangeData.Verified>(relaxed = true)
        coEvery {
            verifiedFiatCalculator.resolveVerifiedState(any<CurrencyCode>(), any<Mint>())
        } returns freshState

        coEvery {
            messagingController.sendRequestToGiveBill(any(), any(), any())
        } returns Result.success(mockk(relaxed = true))

        coEvery {
            messagingController.awaitRequestToGrabBill(any(), any())
        } returns null

        // start() should proceed past exchange data resolution and fail later
        // (at awaitRequestToGrabBill) — confirming the fresh resolve succeeded
        val result = transactor.start()

        assertTrue(result.isFailure)
        // Should NOT be ExchangeRateExpiredException — it recovered via fresh state
        assertIs<GiveBillTransactor.GiveTransactorError.NoGrabReceived>(result.exceptionOrNull())

        unmockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
    }

    @Test
    fun `retry uses relaxed timeout for freshly resolved exchange data`() = runTest {
        val transactor = createTransactor(this)
        val staleState = mockk<VerifiedState>(relaxed = true)
        // Set a strict 30s timeout — mirrors the server-configured value
        setupWith(transactor, verifiedState = staleState, billExchangeDataTimeout = 30.seconds)

        mockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
        // Initial: rate expired with strict 30s timeout
        every { staleState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns null

        // Fresh state: valid exchange data available
        val freshState = mockk<VerifiedState>(relaxed = true)
        every { freshState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns mockk<ExchangeData.Verified>(relaxed = true)
        coEvery {
            verifiedFiatCalculator.resolveVerifiedState(any<CurrencyCode>(), any<Mint>())
        } returns freshState

        coEvery {
            messagingController.sendRequestToGiveBill(any(), any(), any())
        } returns Result.success(mockk(relaxed = true))

        coEvery {
            messagingController.awaitRequestToGrabBill(any(), any())
        } returns null

        val result = transactor.start()

        // Should proceed past exchange resolution and fail at grab — NOT ExchangeRateExpiredException
        assertTrue(result.isFailure)
        assertIs<GiveBillTransactor.GiveTransactorError.NoGrabReceived>(result.exceptionOrNull())

        // Verify the retry path called exchangeDataFor with null (relaxed) timeout,
        // NOT the strict 30s billExchangeDataTimeout
        verify { freshState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), isNull()) }

        unmockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
    }

    @Test
    fun `start fails when send give bill fails`() = runTest {
        val transactor = createTransactor(this)
        val verifiedState = mockk<VerifiedState>(relaxed = true)
        setupWith(transactor, verifiedState = verifiedState)

        mockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
        every { verifiedState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns mockk<ExchangeData.Verified>(relaxed = true)

        coEvery {
            messagingController.sendRequestToGiveBill(any(), any(), any())
        } throws RuntimeException("stream error")

        // sendRequestToGiveBill throws → propagates from start()
        val result = runCatching { transactor.start() }

        assertTrue(result.isFailure || result.getOrNull()?.isFailure == true)

        unmockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
    }

    // endregion

    // region dispose

    @Test
    fun `dispose clears all state`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val transactor = createTransactor(scope)
        setupWith(transactor)

        transactor.dispose()

        // After dispose, data should be empty
        assertTrue(transactor.presentationData.data.isEmpty())
    }

    // endregion

    // region nonce reuse

    @Test
    fun `with() uses provided nonce and passes it to payload factory`() = runTest {
        val capturedNonce = slot<List<Byte>>()
        val factory = PayloadFactory { _, _, nonce ->
            capturedNonce.captured = nonce
            PayloadResult(rendezvous = mockk(relaxed = true), codeData = listOf(1, 2, 3))
        }

        val transactor = GiveBillTransactor(
            messagingController = messagingController,
            transactionController = transactionController,
            scope = this,
            payloadFactory = factory,
            verifiedFiatCalculator = verifiedFiatCalculator,
        )

        val providedNonce = listOf<Byte>(10, 20, 30, 40)
        setupWith(transactor, nonce = providedNonce)

        assertEquals(providedNonce, capturedNonce.captured)
        assertEquals(providedNonce, transactor.presentationData.nonce)
    }

    @Test
    fun `with() generates fresh nonce when none provided`() = runTest {
        val transactor = createTransactor(this)
        setupWith(transactor)

        assertTrue(transactor.presentationData.nonce.isNotEmpty())
    }

    @Test
    fun `calling with() twice with same nonce produces same usedNonce`() = runTest {
        val capturedNonces = mutableListOf<List<Byte>>()
        val factory = PayloadFactory { _, _, nonce ->
            capturedNonces.add(nonce)
            PayloadResult(rendezvous = mockk(relaxed = true), codeData = listOf(1, 2, 3))
        }

        val transactor = GiveBillTransactor(
            messagingController = messagingController,
            transactionController = transactionController,
            scope = this,
            payloadFactory = factory,
            verifiedFiatCalculator = verifiedFiatCalculator,
        )

        val nonce = listOf<Byte>(10, 20, 30, 40)
        setupWith(transactor, nonce = nonce)
        setupWith(transactor, nonce = nonce)

        assertEquals(2, capturedNonces.size)
        assertEquals(capturedNonces[0], capturedNonces[1])
    }

    @Test
    fun `calling with() twice without nonce produces different nonces`() = runTest {
        val capturedNonces = mutableListOf<List<Byte>>()
        val factory = PayloadFactory { _, _, nonce ->
            capturedNonces.add(nonce)
            PayloadResult(rendezvous = mockk(relaxed = true), codeData = listOf(1, 2, 3))
        }

        val transactor1 = GiveBillTransactor(
            messagingController = messagingController,
            transactionController = transactionController,
            scope = this,
            payloadFactory = factory,
            verifiedFiatCalculator = verifiedFiatCalculator,
        )
        val transactor2 = GiveBillTransactor(
            messagingController = messagingController,
            transactionController = transactionController,
            scope = this,
            payloadFactory = factory,
            verifiedFiatCalculator = verifiedFiatCalculator,
        )

        setupWith(transactor1)
        setupWith(transactor2)

        assertEquals(2, capturedNonces.size)
        assertNotEquals(capturedNonces[0], capturedNonces[1])
    }

    // endregion

    // region helpers

    private fun setupWith(
        transactor: GiveBillTransactor,
        verifiedState: VerifiedState? = null,
        nonce: List<Byte>? = null,
        billExchangeDataTimeout: Duration? = null,
    ) {
        val token = mockk<Token>(relaxed = true) {
            every { address } returns Mint.usdf
            every { symbol } returns "USDF"
        }
        val amount = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 500L),
            nativeAmount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD),
            rate = Rate.oneToOne,
            mint = Mint.usdf,
        )
        val owner = mockk<AccountCluster>(relaxed = true) {
            every { withTimelockForToken(any<Token>()) } returns this
            every { vaultPublicKey } returns Key32.mock
        }

        transactor.with(
            token = token,
            amount = amount,
            owner = owner,
            billExchangeDataTimeout = billExchangeDataTimeout,
            verifiedState = verifiedState,
            providedNonce = nonce,
        )
    }

    // endregion
}
