package com.flipcash.app.tokens.ui

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MarketCapExplainerViewModelTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }

        private const val WHOLE_TOKEN = 10_000_000_000L
        private const val SUPPLY_QUARKS = 1_250_000L * WHOLE_TOKEN
    }

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val mint = Mint(List(32) { 1.toByte() })
    private val key = PublicKey.fromBase58("11111111111111111111111111111111")

    private val token: Token = MintMetadata(
        address = mint,
        decimals = 10,
        name = "TestCoin",
        symbol = "TEST",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(vm = key, authority = key, lockDurationInDays = 21),
        launchpadMetadata = LaunchpadMetadata(
            currencyConfig = key,
            liquidityPool = key,
            seed = key,
            authority = key,
            mintVault = key,
            coreMintVault = key,
            currentCirculatingSupplyQuarks = SUPPLY_QUARKS,
            sellFeeBps = 100,
            price = Fiat(fiat = 0.01),
            marketCap = Fiat(fiat = 10000.0),
        ),
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private val appreciation = MutableStateFlow(Fiat.Zero)
    private val rate = MutableStateFlow(Rate.oneToOne)


    private fun kotlinx.coroutines.test.TestScope.newVm(
        cache: Map<Mint, Token> = mapOf(mint to token),
        held: Long? = 12_400L * WHOLE_TOKEN,
    ): MarketCapExplainerViewModel {
        val coordinator = mockk<TokenCoordinator>(relaxed = true)
        every { coordinator.observeTokenCache() } returns MutableStateFlow(cache)
        coEvery { coordinator.getTokenMetadata(any()) } returns Result.failure(IllegalStateException("cache only"))
        every { coordinator.heldQuarksForToken(any()) } returns MutableStateFlow(held)
        every { coordinator.balanceForToken(any<Mint>()) } returns MutableStateFlow(Fiat.Zero)
        every { coordinator.appreciationForToken(any()) } returns appreciation
        val exchange = mockk<Exchange>(relaxed = true)
        every { exchange.observePreferredRate() } returns rate
        return MarketCapExplainerViewModel(coordinator, exchange, TestDispatchers(testScheduler))
    }

    @Test
    fun `providing a cached mint loads the token and a projection at Today`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertEquals(token, state.token)
            val projection = assertNotNull(state.projection)
            assertEquals(true, projection.today().isToday)
        }

    @Test
    fun `a token that is not cached leaves the projection null`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm(cache = emptyMap())
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertNull(vm.stateFlow.value.projection)
        }

    @Test
    fun `appreciation is converted to the preferred currency`() =
        runTest(mainCoroutineRule.dispatcher) {
            rate.value = Rate(fx = 2.0, currency = CurrencyCode.EUR)
            appreciation.value = Fiat(5.0, CurrencyCode.USD)
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertEquals(Rate(fx = 2.0, currency = CurrencyCode.EUR), state.rate)
            assertEquals(CurrencyCode.EUR, state.appreciation?.currencyCode)
            assertEquals(10.0, state.appreciation!!.toDouble(), 1e-9)
        }

    @Test
    fun `negative appreciation keeps its sign after conversion`() =
        runTest(mainCoroutineRule.dispatcher) {
            rate.value = Rate(fx = 2.0, currency = CurrencyCode.EUR)
            appreciation.value = Fiat(-5.0, CurrencyCode.USD)
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertEquals(-10.0, vm.stateFlow.value.appreciation!!.toDouble(), 1e-9)
        }

    @Test
    fun `zero appreciation stays zero`() =
        runTest(mainCoroutineRule.dispatcher) {
            rate.value = Rate.oneToOne
            appreciation.value = Fiat.Zero
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertEquals(0.0, vm.stateFlow.value.appreciation!!.toDouble(), 0.0)
        }

    @Test
    fun `unknown cost basis surfaces as null appreciation`() =
        runTest(mainCoroutineRule.dispatcher) {
            rate.value = Rate(fx = 2.0, currency = CurrencyCode.EUR)
            appreciation.value = Fiat.MIN_VALUE
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertNull(vm.stateFlow.value.appreciation)
        }
}
