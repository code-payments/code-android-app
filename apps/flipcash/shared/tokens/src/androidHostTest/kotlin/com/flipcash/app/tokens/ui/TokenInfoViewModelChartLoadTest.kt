package com.flipcash.app.tokens.ui

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The card-expand overlay reuses one [TokenInfoViewModel] across opens. The chart's history load is
 * triggered by the market cap arriving, so it must fire for each mint even when two mints report the
 * same market cap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenInfoViewModelChartLoadTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val accountController = mockk<AccountController>(relaxed = true)

    private val mintA = Mint(ByteArray(32) { 1 }.toList())
    private val mintB = Mint(ByteArray(32) { 2 }.toList())

    private fun token(mint: Mint): Token = mockk(relaxed = true) {
        every { address } returns mint
        every { marketCap() } returns Fiat(100.0, CurrencyCode.USD)
    }

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { exchange.observePreferredRate() } returns
            MutableStateFlow(Rate(fx = 1.0, currency = CurrencyCode.USD))
        every { accountController.observeHasAccountFor(any()) } returns MutableStateFlow(false)
        every { tokenCoordinator.tokenBalances } returns MutableStateFlow(emptyList())
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns MutableStateFlow(Fiat.Zero)
        every { tokenCoordinator.appreciationForToken(any()) } returns MutableStateFlow(Fiat.Zero)
        coEvery { tokenCoordinator.getHistoricalMarketCapData(any(), any(), any()) } returns
            Result.success(emptyList())

        listOf(mintA, mintB).forEach { mint ->
            val token = token(mint)
            every { tokenCoordinator.cachedToken(mint) } returns token
            coEvery { tokenCoordinator.getTokenMetadata(mint) } returns
                Result.success(TokenResult(token, DataSource.Cache))
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    @Test
    fun `opening a second mint with the same market cap loads its chart`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = TokenInfoViewModel(
                accountController = accountController,
                tokenCoordinator = tokenCoordinator,
                exchange = exchange,
                shareController = mockk<ShareSheetController>(relaxed = true),
                resources = FakeResourceHelper(),
                purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
                feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true),
                dispatchers = TestDispatchers(testScheduler),
            )

            vm.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mintA, resetChartPeriod = true))
            advanceUntilIdle()
            coVerify(atLeast = 1) { tokenCoordinator.getHistoricalMarketCapData(mintA, any(), any()) }

            vm.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mintB, resetChartPeriod = true))
            advanceUntilIdle()
            coVerify(atLeast = 1) { tokenCoordinator.getHistoricalMarketCapData(mintB, any(), any()) }
        }
}
