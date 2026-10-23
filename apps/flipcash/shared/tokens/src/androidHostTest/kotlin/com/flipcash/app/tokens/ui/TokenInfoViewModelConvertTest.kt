package com.flipcash.app.tokens.ui

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
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
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Convert on the Dollars screen is enabled only when the user holds another currency with a
 * displayable balance. Bugsnag 6ac36f88991833fd09434aad: a Dollars-only user reached the convert
 * flow with a leftover zero-balance USDC row as the only "other" holding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenInfoViewModelConvertTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val accountController = mockk<AccountController>(relaxed = true)
    private val balances = MutableStateFlow<List<TokenWithBalance>>(emptyList())

    private val other = Mint(ByteArray(32) { 2 }.toList())

    private fun held(mint: Mint, usd: Double): TokenWithBalance {
        val token = mockk<Token>(relaxed = true) { every { address } returns mint }
        return mockk(relaxed = true) {
            every { this@mockk.token } returns token
            every { this@mockk.balance } returns Fiat(usd)
        }
    }

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { exchange.observePreferredRate() } returns
            MutableStateFlow(Rate(fx = 1.0, currency = CurrencyCode.USD))
        every { accountController.observeHasAccountFor(any()) } returns MutableStateFlow(false)
        every { tokenCoordinator.tokenBalances } returns balances
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns MutableStateFlow(Fiat.Zero)
        every { tokenCoordinator.appreciationForToken(any()) } returns MutableStateFlow(Fiat.Zero)
        coEvery { tokenCoordinator.getHistoricalMarketCapData(any(), any(), any()) } returns
            Result.success(emptyList())
        listOf(Mint.usdf, other).forEach { mint ->
            val token = mockk<Token>(relaxed = true) { every { address } returns mint }
            every { tokenCoordinator.cachedToken(mint) } returns token
            coEvery { tokenCoordinator.getTokenMetadata(mint) } returns
                Result.success(TokenResult(token, DataSource.Cache))
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun TestScope.openToken(mint: Mint): TokenInfoViewModel {
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
        vm.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint))
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.openedRoutes(vm: TokenInfoViewModel): List<AppRoute> {
        val routes = mutableListOf<AppRoute>()
        vm.eventFlow.filterIsInstance<TokenInfoViewModel.Event.OpenScreen>()
            .onEach { routes += it.screen }
            .launchIn(backgroundScope)
        return routes
    }

    @Test
    fun `Convert from Dollars is disabled while balances load`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = openToken(Mint.usdf)
        assertFalse(vm.stateFlow.value.canConvert)
    }

    @Test
    fun `Convert from Dollars is disabled when only Dollars is held`() = runTest(mainCoroutineRule.dispatcher) {
        balances.value = listOf(held(Mint.usdf, 5.0))
        val vm = openToken(Mint.usdf)
        val routes = openedRoutes(vm)

        assertFalse(vm.stateFlow.value.canConvert)
        vm.dispatchEvent(TokenInfoViewModel.Event.OnConvert)
        advanceUntilIdle()
        assertTrue(routes.isEmpty(), routes.toString())
    }

    @Test
    fun `Convert from Dollars is disabled when the only other holding is empty`() =
        runTest(mainCoroutineRule.dispatcher) {
            balances.value = listOf(held(Mint.usdf, 5.0), held(other, 0.0))
            val vm = openToken(Mint.usdf)
            assertFalse(vm.stateFlow.value.canConvert)
        }

    @Test
    fun `Convert from Dollars is enabled when another currency has a balance`() =
        runTest(mainCoroutineRule.dispatcher) {
            balances.value = listOf(held(Mint.usdf, 5.0), held(other, 2.0))
            val vm = openToken(Mint.usdf)
            val routes = openedRoutes(vm)

            assertTrue(vm.stateFlow.value.canConvert)
            vm.dispatchEvent(TokenInfoViewModel.Event.OnConvert)
            advanceUntilIdle()
            val route = assertIs<AppRoute.Token.Swap>(routes.single())
            assertEquals(SwapPurpose.Convert(mint = Mint.usdf, destinationMint = Mint.usdf), route.purpose)
        }

    @Test
    fun `Convert from another currency stays enabled with only Dollars held`() =
        runTest(mainCoroutineRule.dispatcher) {
            balances.value = listOf(held(Mint.usdf, 5.0))
            val vm = openToken(other)
            assertTrue(vm.stateFlow.value.canConvert)
        }
}
