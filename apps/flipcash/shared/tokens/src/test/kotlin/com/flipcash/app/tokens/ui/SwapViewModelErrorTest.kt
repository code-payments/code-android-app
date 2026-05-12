package com.flipcash.app.tokens.ui

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val transactionController: TransactionOperations = mock()
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)
    private val coinbaseOnRampController = mockk<CoinbaseOnRampController>(relaxed = true)

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
        every { resources.getString(R.string.error_title_buySellFailed) } returns "error_title_buySellFailed"
        every { resources.getString(R.string.error_description_buySellFailed) } returns "error_description_buySellFailed"

        // Stub limits StateFlow so init block doesn't NPE on null flow
        whenever(transactionController.limits).thenReturn(MutableStateFlow(null))
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic("com.getcode.opencode.utils.PublicKeyKt")
    }

    private fun createViewModel(): SwapViewModel {
        return SwapViewModel(
            userManager = userManager,
            exchange = exchange,
            verifiedFiatCalculator = verifiedFiatCalculator,
            transactionController = transactionController,
            resources = resources,
            tokenCoordinator = tokenCoordinator,
            feedCoordinator = feedCoordinator,
            analytics = analytics,
            purchaseMethodController = purchaseMethodController,
            coinbaseOnRampController = coinbaseOnRampController,
            dispatchers = dispatchers,
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
}
