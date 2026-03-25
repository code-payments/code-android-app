package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.extensions.exchangeDataFor
import com.getcode.opencode.internal.manager.VerifiedProtoManager
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GiveBillTransactorTest {

    private val currencyController = mockk<CurrencyController>(relaxed = true)
    private val messagingController = mockk<MessagingController>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val verifiedProtoManager = mockk<VerifiedProtoManager>(relaxed = true)

    private val payloadFactory = PayloadFactory { _, _, _ ->
        PayloadResult(rendezvous = mockk(relaxed = true), codeData = emptyList())
    }

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    private fun createTransactor(scope: TestScope): GiveBillTransactor {
        return GiveBillTransactor(
            currencyController = currencyController,
            messagingController = messagingController,
            transactionController = transactionController,
            scope = scope,
            verifiedProtoManager = verifiedProtoManager,
            payloadFactory = payloadFactory,
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
    fun `start fails when no verified state provided and none resolvable`() = runTest {
        val transactor = createTransactor(this)
        // Pass verifiedState = null, proto store returns null → resolveVerifiedState
        // will call getLiveMintData which throws (MockK can't mock Result<Unit> returns)
        // → resolveVerifiedState returns null → start() returns failure
        setupWith(transactor, verifiedState = null)

        every { verifiedProtoManager.getVerifiedStateFor(any(), any()) } returns null
        // getLiveMintData throws → runCatching in resolveVerifiedState catches it → returns null
        coEvery { currencyController.getLiveMintData(any(), any(), any()) } throws RuntimeException("no data")

        // Use runCatching because the thrown exception may propagate
        val result = runCatching { transactor.start() }

        assertTrue(result.isFailure || result.getOrNull()?.isFailure == true)
    }

    @Test
    fun `start fails when exchange data expired`() = runTest {
        val transactor = createTransactor(this)
        // Provide verified state directly to skip resolveVerifiedState fallback chain
        val verifiedState = mockk<VerifiedState>(relaxed = true)
        setupWith(transactor, verifiedState = verifiedState)

        mockkStatic("com.getcode.opencode.internal.extensions.VerifiedStateKt")
        every { verifiedState.exchangeDataFor(any<LocalFiat>(), any<Mint>(), any()) } returns null

        val result = transactor.start()

        assertTrue(result.isFailure)
        assertIs<GiveBillTransactor.GiveTransactorError.ExchangeRateExpiredException>(result.exceptionOrNull())

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
        assertTrue(transactor.data.isEmpty())
    }

    // endregion

    // region helpers

    private fun setupWith(
        transactor: GiveBillTransactor,
        verifiedState: VerifiedState? = null,
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
            billExchangeDataTimeout = null,
            verifiedState = verifiedState,
        )
    }

    // endregion
}
