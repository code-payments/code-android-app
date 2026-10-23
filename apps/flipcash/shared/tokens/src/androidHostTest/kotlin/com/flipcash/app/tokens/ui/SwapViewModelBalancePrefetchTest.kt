package com.flipcash.app.tokens.ui

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.UsdcDepositSweep
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sell/convert insufficient-funds check reads the cached balance. The receipt screen starts a
 * refresh when it appears; the confirm tap joins it if still running instead of paying the
 * network round trip itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelBalancePrefetchTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController: TransactionOperations = mock()
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val accountCluster = mockk<AccountCluster>(relaxed = true)
    private val accountController = mockk<AccountController>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    /** What the cached balance currently reads; a refresh may overwrite it. */
    private var cachedBalance = Fiat(999_999.0)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        mockkStatic("com.getcode.opencode.utils.PublicKeyKt")
        every { PublicKey.generate() } returns mockk<PublicKey>(relaxed = true)
        every { userManager.accountCluster } returns accountCluster
        every { accountController.observeHasAccountFor(any()) } returns MutableStateFlow(false)
        whenever(transactionController.limits).thenReturn(MutableStateFlow(null))
        every { tokenCoordinator.tokenBalances } returns
            MutableStateFlow(listOf(mockk<TokenWithBalance>(relaxed = true)))
        every { tokenCoordinator.balanceForToken(any<Token>()) } answers { cachedBalance }
        transactionController.stub {
            onBlocking { sell(any(), any(), any()) } doReturn
                Result.failure(RuntimeException("sell failed"))
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic("com.getcode.opencode.utils.PublicKeyKt")
    }

    private fun createViewModel() = SwapViewModel(
        userManager = userManager,
        accountController = accountController,
        exchange = mockk<Exchange>(relaxed = true),
        verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true),
        transactionController = transactionController,
        resources = FakeResourceHelper(),
        tokenCoordinator = tokenCoordinator,
        feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true),
        analytics = RecordingAnalytics(),
        purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
        coinbaseOnRampController = mockk<CoinbaseOnRampController>(relaxed = true),
        phantomWalletController = mockk<PhantomWalletController>(relaxed = true),
        dispatchers = dispatchers,
        userFlags = mockk<UserFlagsCoordinator>(relaxed = true),
        usdcDepositSweep = mockk<UsdcDepositSweep>(relaxed = true),
        balancePoller = mockk<com.flipcash.app.tokens.BalancePoller>(relaxed = true),
    )

    private fun SwapViewModel.primeForSell() {
        val token = mockk<Token>(relaxed = true) { every { address } returns Mint.usdf }
        val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        dispatchEvent(SwapViewModel.Event.OnPurposeChanged(SwapPurpose.Sell(mockk(relaxed = true))))
        dispatchEvent(SwapViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
    }

    /** A $5 sell of the USDF mint (quarks map 1:1 to USD at 6 decimals). */
    private fun sellAmount(): VerifiedFiat {
        val local = mockk<LocalFiat>(relaxed = true) {
            every { underlyingTokenAmount } returns Fiat(5_000_000L, CurrencyCode.USD)
        }
        return VerifiedFiat(local, null)
    }

    private fun insufficientFundsShown() =
        BottomBarManager.messages.value.any { it.title == "error_title_insufficientFunds" }

    @Test
    fun `prefetch finished before tap submits without another refresh`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            coEvery { tokenCoordinator.updateTokenAccount(any<Mint>()) } returns Unit

            val vm = createViewModel().apply { primeForSell() }
            vm.dispatchEvent(SwapViewModel.Event.ConfirmationShown)
            advanceUntilIdle()
            coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(Mint.usdf) }

            vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(sellAmount()))
            advanceUntilIdle()

            coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(any<Mint>()) }
            verify(transactionController, times(1)).sell(any(), any(), any())
            assertFalse(insufficientFundsShown())
        }

    @Test
    fun `prefetch in flight at tap is awaited and its balance is used`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val gate = CompletableDeferred<Unit>()
            coEvery { tokenCoordinator.updateTokenAccount(any<Mint>()) } coAnswers {
                gate.await()
                cachedBalance = Fiat(1.0) // refreshed balance is below the $5 sell
            }

            val vm = createViewModel().apply { primeForSell() }
            vm.dispatchEvent(SwapViewModel.Event.ConfirmationShown)
            vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(sellAmount()))
            advanceUntilIdle()

            // Still waiting on the refresh: nothing submitted, no alert yet, no second refresh.
            verify(transactionController, never()).sell(any(), any(), any())
            assertFalse(insufficientFundsShown())
            coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(any<Mint>()) }

            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(insufficientFundsShown())
            verify(transactionController, never()).sell(any(), any(), any())
            coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(any<Mint>()) }
        }

    @Test
    fun `no prefetch awaits a refresh at tap`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        coEvery { tokenCoordinator.updateTokenAccount(any<Mint>()) } coAnswers {
            cachedBalance = Fiat(1.0)
        }

        val vm = createViewModel().apply { primeForSell() }
        vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(sellAmount()))
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(Mint.usdf) }
        assertTrue(insufficientFundsShown())
        verify(transactionController, never()).sell(any(), any(), any())
    }

    @Test
    fun `failed prefetch falls back to the cached balance`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        coEvery { tokenCoordinator.updateTokenAccount(any<Mint>()) } throws RuntimeException("offline")

        val vm = createViewModel().apply { primeForSell() }
        vm.dispatchEvent(SwapViewModel.Event.ConfirmationShown)
        advanceUntilIdle()
        vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(sellAmount()))
        advanceUntilIdle()

        coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(any<Mint>()) }
        assertFalse(insufficientFundsShown())
        verify(transactionController, times(1)).sell(any(), any(), any())
    }

    @Test
    fun `receipt reappearing while a sell is submitting does not refetch`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            coEvery { tokenCoordinator.updateTokenAccount(any<Mint>()) } returns Unit
            // Hold the submit open: the sell is in flight when the receipt re-appears.
            val gate = CompletableDeferred<Unit>()
            transactionController.stub {
                onBlocking { sell(any(), any(), any()) } doSuspendableAnswer {
                    gate.await()
                    Result.failure(RuntimeException("sell failed"))
                }
            }

            val vm = createViewModel().apply { primeForSell() }
            vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(sellAmount()))
            advanceUntilIdle()
            vm.dispatchEvent(SwapViewModel.Event.ConfirmationShown)
            advanceUntilIdle()

            // Only the tap's own refresh; a refetch here could overwrite the subtracted balance.
            coVerify(exactly = 1) { tokenCoordinator.updateTokenAccount(any<Mint>()) }
            gate.complete(Unit)
            advanceUntilIdle()
        }
}
