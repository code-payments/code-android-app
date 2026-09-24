package com.flipcash.app.tokens.ui

import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.analytics.AddMoneyMethod
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.WalletProvider
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.analytics.events.WalletEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.UsdcDepositSweep
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.FakeResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.onramp.OrderDeliveryResult
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.userflags.UserFlagsCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val accountController = mockk<AccountController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val transactionController: TransactionOperations = mock()
    private val resources = FakeResourceHelper()
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    private val analytics = RecordingAnalytics()
    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)
    private val coinbaseOnRampController = mockk<CoinbaseOnRampController>(relaxed = true)
    private val phantomWalletController = mockk<PhantomWalletController>(relaxed = true)
    private val userFlagsCoordinator = mockk<UserFlagsCoordinator>(relaxed = true)
    private val usdcDepositSweep = mockk<UsdcDepositSweep>(relaxed = true)

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()

        // Mock PublicKey.generate() to prevent Ed25519 native lib load
        // (TransactionController.buy default param evaluates SwapFundingSource.SubmitIntent which calls Ed25519)
        mockkStatic("com.getcode.opencode.utils.PublicKeyKt")
        every { PublicKey.generate() } returns mockk<PublicKey>(relaxed = true)

        every { userManager.accountCluster } returns accountCluster

        // A Get asks whether the mint already has a token account to decide first-buy vs top-up.
        // Nothing here exercises that branch, so answer "no" rather than leaving it on a relaxed
        // mock's empty flow.
        every { accountController.observeHasAccountFor(any()) } returns MutableStateFlow(false)

        // Stub limits StateFlow so init block doesn't NPE on null flow
        whenever(transactionController.limits).thenReturn(MutableStateFlow(null))

        // A Get seeds its funding source from the first non-empty balance snapshot. Serve one
        // right away so that chain finishes: it awaits the snapshot inside the shared event
        // bus's collector, and a collector parked there would stall every later dispatch. The
        // relaxed balance isn't displayable, so nothing is actually seeded.
        every { tokenCoordinator.tokenBalances } returns
            MutableStateFlow(listOf(mockk<TokenWithBalance>(relaxed = true)))
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic("com.getcode.opencode.utils.PublicKeyKt")
    }

    private fun createViewModel(): SwapViewModel {
        return SwapViewModel(
            userManager = userManager,
            accountController = accountController,
            exchange = exchange,
            verifiedFiatCalculator = verifiedFiatCalculator,
            transactionController = transactionController,
            resources = resources,
            tokenCoordinator = tokenCoordinator,
            feedCoordinator = feedCoordinator,
            analytics = analytics,
            purchaseMethodController = purchaseMethodController,
            coinbaseOnRampController = coinbaseOnRampController,
            phantomWalletController = phantomWalletController,
            dispatchers = dispatchers,
            userFlags = userFlagsCoordinator,
            usdcDepositSweep = usdcDepositSweep,
        )
    }

    @Test
    fun `buy failure shows buySellFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        transactionController.stub {
            onBlocking { buy(any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull()) } doReturn
                Result.failure(RuntimeException("buy failed"))
        }

        val token = mockk<Token>(relaxed = true)
        val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        val amount = VerifiedFiat(mockk<LocalFiat>(relaxed = true), null)

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.OnPurposeChanged(SwapPurpose.Buy(mockk(relaxed = true))))
        vm.dispatchEvent(SwapViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
        vm.dispatchEvent(SwapViewModel.Event.ProceedWithPurchase(amount))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_buySellFailed" })
    }

    @Test
    fun `sell failure shows buySellFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        // Ensure balance check passes so the sell call is reached
        every { tokenCoordinator.balanceForToken(any<Token>()) } returns Fiat(999_999.0)
        whenever(transactionController.sell(any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("sell failed")))

        val token = mockk<Token>(relaxed = true) {
            every { address } returns Mint.usdf
        }
        val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        val amount = VerifiedFiat(mockk<LocalFiat>(relaxed = true), null)

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.OnPurposeChanged(SwapPurpose.Sell(mockk(relaxed = true))))
        vm.dispatchEvent(SwapViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
        vm.dispatchEvent(SwapViewModel.Event.ProceedWithSale(amount))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_buySellFailed" })
    }

    @Test
    fun `coinbase deposit waits for delivery before sweeping`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { tokenCoordinator.balanceForToken(Mint.usdf) } returns MutableStateFlow(Fiat.Zero)
        coEvery { coinbaseOnRampController.awaitOrderDelivered("order-1") } returns
            OrderDeliveryResult.Delivered(txHash = "sig")

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.DepositSubmitted(orderId = "order-1"))
        advanceUntilIdle()

        // Coinbase delivery must be confirmed on-chain before the sweep runs.
        coVerify { coinbaseOnRampController.awaitOrderDelivered("order-1") }
        verify { usdcDepositSweep.execute(accountCluster) }
    }

    @Test
    fun `coinbase deposit does not sweep when delivery fails`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { tokenCoordinator.balanceForToken(Mint.usdf) } returns MutableStateFlow(Fiat.Zero)
        coEvery { coinbaseOnRampController.awaitOrderDelivered("order-2") } returns
            OrderDeliveryResult.Failed

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.DepositSubmitted(orderId = "order-2"))
        advanceUntilIdle()

        verify(exactly = 0) { usdcDepositSweep.execute(any()) }
    }

    // --- Wallet (Phantom) events -----------------------------------------------------------

    @Test
    fun `connecting phantom successfully tracks Wallet Connect`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        coEvery { phantomWalletController.connectWallet() } returns Result.success(Unit)

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.StartPhantomCeremony)
        advanceUntilIdle()

        assertEquals(
            WalletEvents.connect(OnRampProvider.Phantom.analytics),
            analytics.events.single(),
        )
    }

    @Test
    fun `user rejecting the phantom connect tracks Wallet Cancel`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        coEvery { phantomWalletController.connectWallet() } returns Result.failure(
            DeeplinkOnRampError.WalletProvidedError(
                DeeplinkError.UserRejectedRequest,
                message = "User cancelled connection",
            )
        )

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.StartPhantomCeremony)
        advanceUntilIdle()

        assertEquals(
            WalletEvents.cancel(OnRampProvider.Phantom.analytics),
            analytics.events.single(),
        )
    }

    @Test
    fun `a failed send during the phantom connect tracks Wallet Transactions Failed`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            coEvery { phantomWalletController.connectWallet() } returns Result.failure(
                DeeplinkOnRampError.FailedToSendTransaction(code = 7L, message = "boom")
            )

            val vm = createViewModel()
            vm.dispatchEvent(SwapViewModel.Event.StartPhantomCeremony)
            advanceUntilIdle()

            assertEquals(
                WalletEvents.transactionsFailed(OnRampProvider.Phantom.analytics),
                analytics.events.single { it.name == "Wallet: Transactions Failed" },
            )
        }

    @Test
    fun `confirming a phantom transaction tracks Wallet Request Amount with the token amount`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { exchange.preferredRate } returns Rate.oneToOne

            val verifiedFiat = VerifiedFiat(
                LocalFiat.fromUsd(usdf = Fiat(12.0, CurrencyCode.USD)),
                null,
            )
            coEvery {
                verifiedFiatCalculator.compute(any(), any(), any(), any(), any())
            } returns Result.success(verifiedFiat)

            val token = mockk<Token>(relaxed = true) {
                every { address } returns Mint.usdf
            }
            val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
                every { this@mockk.token } returns token
            }

            // Fail the swap after the pre-sign callback fires, rather than succeeding — a
            // successful WithSwapId result drives an unrelated swap-polling collector that
            // would need its own mocking and isn't part of what this test is checking.
            val swapId = SwapId(ByteArray(32) { 0 }.toList())
            coEvery {
                phantomWalletController.executeSwap(any(), any(), any(), any())
            } coAnswers {
                val onBeforeSign = arg<(suspend (SwapId) -> Unit)?>(3)
                onBeforeSign?.invoke(swapId)
                Result.failure(DeeplinkOnRampError.FailedToCreateTransaction(message = "boom"))
            }

            val vm = createViewModel()
            vm.dispatchEvent(
                SwapViewModel.Event.OnPurposeChanged(
                    SwapPurpose.Buy(Mint.usdf, fundingSource = FundingSource.Phantom)
                )
            )
            vm.dispatchEvent(SwapViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
            vm.amountDelegate.setAmount(12.0)
            vm.dispatchEvent(SwapViewModel.Event.ConfirmPhantomTransaction)
            advanceUntilIdle()

            assertEquals(
                WalletEvents.requestAmount(
                    OnRampProvider.Phantom.analytics,
                    verifiedFiat.localFiat.underlyingTokenAmount.analytics,
                ),
                analytics.events.single { it.name == "Wallet: Request Amount" },
            )
        }

    @Test
    fun `a failed Coinbase delivery tracks Add Money as a failure`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { tokenCoordinator.balanceForToken(Mint.usdf) } returns MutableStateFlow(Fiat.Zero)
        coEvery { coinbaseOnRampController.awaitOrderDelivered("order-3") } returns
            OrderDeliveryResult.Failed

        val vm = createViewModel()
        vm.dispatchEvent(
            SwapViewModel.Event.OnPurposeChanged(SwapPurpose.Buy(Mint.usdf, fundingSource = FundingSource.Coinbase))
        )
        vm.dispatchEvent(SwapViewModel.Event.DepositSubmitted(orderId = "order-3"))
        advanceUntilIdle()

        val result = analytics.events.single { it.name == "Add Money" }
        // The amount block is the net of the entry and its fee; only its shape is fixed here.
        assertIs<PropertyValue.Number>(result.properties["Fiat"])
        val amountKeys = setOf("Fiat", "Currency", "USDC", "Quarks", "Exchange Rate", "Mint")
        assertEquals(
            AddMoneyEvents.result(AddMoneyMethod.COINBASE, AnalyticsState.FAILURE, amount = null, error = "Order delivery failed"),
            result.copy(properties = result.properties - amountKeys),
        )
    }

    @Test
    fun `a deposit outside a USDF buy tracks no Add Money result`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { tokenCoordinator.balanceForToken(Mint.usdf) } returns MutableStateFlow(Fiat.Zero)
        coEvery { coinbaseOnRampController.awaitOrderDelivered("order-4") } returns
            OrderDeliveryResult.Failed

        val vm = createViewModel()
        vm.dispatchEvent(SwapViewModel.Event.DepositSubmitted(orderId = "order-4"))
        advanceUntilIdle()

        assertTrue(analytics.events.none { it.name == "Add Money" })
    }

    // --- Entry-currency conversion on the buy gate ---------------------------------------------
    //
    // transactionLimit() is USD-denominated. A user entering in a currency that trades well below
    // 1:1 was blocked on any amount, because the gate read the typed number as dollars: 500 naira
    // was compared against the ~$0.87 held rather than the $0.33 it is worth.

    /** ~1,530 NGN to the dollar, the rate at the time this was reported. */
    private val ngnPerUsd = 1530.0

    private fun nairaBuyHolding(usd: Double): SwapViewModel {
        every { exchange.rateToUsd(CurrencyCode.NGN) } returns
            Rate(fx = 1 / ngnPerUsd, currency = CurrencyCode.USD)

        // A daily limit high enough to never be the binding constraint; the balance is what this
        // exercises. Left as `null`, sendLimitFor() answers SendLimit.Zero and every entry is over.
        whenever(transactionController.limits).thenReturn(
            MutableStateFlow(
                Limits(
                    sinceDate = 0L,
                    fetchDate = System.currentTimeMillis(),
                    sendLimits = mapOf(
                        CurrencyCode.USD to SendLimit(
                            nextTransaction = 10_000.0,
                            maxPerTransaction = 10_000.0,
                            maxPerDay = 10_000.0,
                        )
                    ),
                    amountUsdTransactedSinceConsumption = Fiat(0.0),
                )
            )
        )

        val fundingToken = mockk<Token>(relaxed = true) {
            every { address } returns Mint.usdf
        }
        val held = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns fundingToken
            every { this@mockk.balance } returns Fiat(usd)
        }
        every { tokenCoordinator.tokenBalances } returns MutableStateFlow(listOf(held))

        val vm = createViewModel()
        // Flexible funding is the Get path, the one that caps on the held balance.
        vm.dispatchEvent(
            SwapViewModel.Event.OnPurposeChanged(
                SwapPurpose.Buy(
                    mint = Mint(ByteArray(32) { 1 }.toList()),
                    fundingSource = FundingSource.Flexible,
                )
            )
        )
        vm.amountDelegate.onCurrencyChanged(Currency(code = "NGN", name = "Nigerian Naira"))
        return vm
    }

    @Test
    fun `buy gate converts the entered amount out of the entry currency`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = nairaBuyHolding(usd = 0.87)
            advanceUntilIdle()

            vm.amountDelegate.setAmount(500.0)
            advanceUntilIdle()

            // 500 NGN is ~$0.33 — inside the $0.87 held, so the buy proceeds.
            assertFalse(vm.checkFundingAmount())
            assertTrue(BottomBarManager.messages.value.isEmpty())
        }

    @Test
    fun `buy gate blocks an entry above the converted balance`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = nairaBuyHolding(usd = 0.87)
            advanceUntilIdle()

            vm.amountDelegate.setAmount(5_000.0)
            advanceUntilIdle()

            // 5,000 NGN is ~$3.27 — over the $0.87 held.
            assertTrue(vm.checkFundingAmount())
            assertTrue(
                BottomBarManager.messages.value.any { it.title == "title_insufficientBalance" }
            )
        }

    @Test
    fun `buy gate blocks when no rate is available to convert with`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = nairaBuyHolding(usd = 0.87)
            every { exchange.rateToUsd(CurrencyCode.NGN) } returns null
            advanceUntilIdle()

            // Deliberately under the $0.87 ceiling read as a raw number, so the only thing that
            // can block this is the missing-rate guard itself.
            vm.amountDelegate.setAmount(0.5)
            advanceUntilIdle()

            // Rate.ignore would convert this to ~0 and clear every ceiling. Fail closed instead.
            assertTrue(vm.checkFundingAmount())
        }
}
