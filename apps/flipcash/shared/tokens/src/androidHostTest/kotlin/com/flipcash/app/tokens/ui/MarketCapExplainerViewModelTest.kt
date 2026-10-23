package com.flipcash.app.tokens.ui

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        fetched: Token? = null,
        fetch: (suspend () -> Result<TokenResult>)? = null,
    ): MarketCapExplainerViewModel {
        val coordinator = mockk<TokenCoordinator>(relaxed = true)
        every { coordinator.observeTokenCache() } returns MutableStateFlow(cache)
        coEvery { coordinator.getTokenMetadata(any()) } coAnswers {
            fetch?.invoke() ?: when (fetched) {
                null -> Result.failure(IllegalStateException("cache only"))
                else -> Result.success(TokenResult(fetched, DataSource.Network))
            }
        }
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
    fun `a token that is neither cached nor fetchable is unavailable`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm(cache = emptyMap())
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertNull(state.projection)
            assertTrue(state.unavailable)
            assertFalse(state.isLoading)
        }

    @Test
    fun `an uncached token is loading while its fetch is in flight`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm(cache = emptyMap(), fetch = { awaitCancellation() })
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertTrue(state.isLoading)
            assertFalse(state.unavailable)
        }

    @Test
    fun `a token without a bonding supply is unavailable`() =
        runTest(mainCoroutineRule.dispatcher) {
            val unlaunched = (token as MintMetadata).copy(launchpadMetadata = null)
            val vm = newVm(cache = mapOf(mint to unlaunched))
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertTrue(vm.stateFlow.value.unavailable)
        }

    @Test
    fun `a fetched token that the coordinator does not cache still loads a projection`() =
        runTest(mainCoroutineRule.dispatcher) {
            // The coordinator only caches network tokens the user has an account for, so a token
            // opened from Discovery arrives through getTokenMetadata alone.
            val vm = newVm(cache = emptyMap(), held = null, fetched = token)
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertEquals(token, state.token)
            assertNotNull(state.projection)
            assertNotNull(state.labels)
        }

    @Test
    fun `a user with tokens held is a holder`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertTrue(vm.stateFlow.value.holdsToken)
        }

    @Test
    fun `a known zero balance is not a holder`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm(held = null)
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.holdsToken)
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

    @Test
    fun `fixed labels are formatted in the preferred currency and follow a rate change`() =
        runTest(mainCoroutineRule.dispatcher) {
            val vm = newVm()
            vm.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
            advanceUntilIdle()
            val usdTicks = assertNotNull(vm.stateFlow.value.labels).ticks
            assertEquals("$5K", usdTicks.entries.first { it.key.reserve.compareTo(BigDecimal(5_000)) == 0 }.value)

            rate.value = Rate(fx = 0.5, currency = CurrencyCode.EUR)
            advanceUntilIdle()

            val labels = assertNotNull(vm.stateFlow.value.labels)
            val byReserve = labels.ticks.mapKeys { it.key.reserve.toInt() }
            // 5,000 USD is 2,500 EUR; 10M USD is 5M EUR.
            assertEquals("\u20ac2.5K", byReserve[5_000])
            assertEquals("\u20ac5M", byReserve[10_000_000])
            assertEquals("12,400", labels.tokensHeld)
        }

    @Test
    fun `price keeps four significant figures in the preferred currency`() {
        assertEquals("$0.02994", MarketCapExplainerViewModel.price(BigDecimal("0.02994"), Rate.oneToOne))
        // 0.02994 USD is 0.01497 EUR.
        assertEquals("\u20ac0.01497", MarketCapExplainerViewModel.price(BigDecimal("0.02994"), Rate(fx = 0.5, currency = CurrencyCode.EUR)))
        assertEquals("\u00a54.491", MarketCapExplainerViewModel.price(BigDecimal("0.02994"), Rate(fx = 150.0, currency = CurrencyCode.JPY)))
    }

    @Test
    fun `a tiny non-zero share reads as less than a hundredth of a percent`() {
        val share = BigDecimal(597).multiply(BigDecimal(100)).divide(BigDecimal(21_000_000), MathContext(20))
        assertEquals("<0.01%", MarketCapExplainerViewModel.percent(share))
        assertEquals("<0.01%", MarketCapExplainerViewModel.percent(BigDecimal("0.0099")))
        assertEquals("0.01%", MarketCapExplainerViewModel.percent(BigDecimal("0.01")))
        assertEquals("0.99%", MarketCapExplainerViewModel.percent(BigDecimal("0.992")))
        assertEquals("0%", MarketCapExplainerViewModel.percent(BigDecimal.ZERO))
    }
}
