package com.flipcash.app.cash.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.cash.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CashScreenViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resources: ResourceHelper = mockk(relaxed = true)
    private val exchange: Exchange = mockk(relaxed = true)
    private val verifiedFiatCalculator: VerifiedFiatCalculator = mockk(relaxed = true)
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true)
    private val transactionController: TransactionOperations = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    private val updateStateForEvent = CashScreenViewModel.updateStateForEvent

    @Before
    fun setUp() {
        BottomBarManager.clear()

        // Stub resource strings used by the ViewModel
        every { resources.getString(R.string.error_title_youNeedMoreCash) } returns "error_title_youNeedMoreCash"
        every { resources.getString(R.string.error_description_youNeedMoreCash) } returns "error_description_youNeedMoreCash"
        every { resources.getString(R.string.action_addMoreCash) } returns "action_addMoreCash"
        every { resources.getString(R.string.action_dismiss) } returns "action_dismiss"
        every { resources.getString(R.string.error_title_sendLimitReached) } returns "error_title_sendLimitReached"
        every { resources.getString(R.string.error_description_sendLimitReached) } returns "error_description_sendLimitReached"

        // Default stubs for flows consumed in init
        every { exchange.observePreferredRate() } returns emptyFlow()
        every { exchange.preferredRate } returns Rate.oneToOne
        every { tokenCoordinator.observeSelectedTokenMint() } returns emptyFlow()
        every { tokenCoordinator.tokens } returns emptyFlow()
        every { transactionController.limits } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): CashScreenViewModel {
        return CashScreenViewModel(
            resources = resources,
            exchange = exchange,
            verifiedFiatCalculator = verifiedFiatCalculator,
            tokenCoordinator = tokenCoordinator,
            transactionController = transactionController,
            dispatchers = dispatchers,
        )
    }

    // ---------------------------------------------------------------
    // State reducer tests (pure, no mocking needed beyond companion)
    // ---------------------------------------------------------------

    @Test
    fun `updateStateForEvent OnTokenSelected updates selectedTokenAddress`() {
        val mint = Mint("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6")
        val state = CashScreenViewModel.State()
        val updated = updateStateForEvent(CashScreenViewModel.Event.OnTokenSelected(mint))(state)
        assertEquals(mint, updated.selectedTokenAddress)
    }

    @Test
    fun `updateStateForEvent OnTokenUpdated sets token`() {
        val token: Token = mockk(relaxed = true)
        val balance = LocalFiat.Zero
        val tokenWithBalance = TokenWithLocalizedBalance(token = token, balance = balance)
        val state = CashScreenViewModel.State()
        val updated = updateStateForEvent(CashScreenViewModel.Event.OnTokenUpdated(tokenWithBalance))(state)
        assertEquals(tokenWithBalance, updated.token)
    }

    @Test
    fun `updateStateForEvent OnMaxDetermined sets maxForGive`() {
        val state = CashScreenViewModel.State()
        val updated = updateStateForEvent(
            CashScreenViewModel.Event.OnMaxDetermined(max = 100.0, currencyCode = CurrencyCode.USD)
        )(state)
        assertNotNull(updated.maxForGive)
        assertEquals(100.0, updated.maxForGive!!.first)
        assertEquals(CurrencyCode.USD, updated.maxForGive!!.second)
    }

    @Test
    fun `updateStateForEvent OnLimitsChanged sets limits`() {
        val limits = Limits(
            sinceDate = 0L,
            fetchDate = 0L,
            sendLimits = mapOf(CurrencyCode.USD to SendLimit(100.0, 500.0, 1000.0)),
            amountUsdTransactedSinceConsumption = Fiat.Zero,
        )
        val state = CashScreenViewModel.State()
        val updated = updateStateForEvent(CashScreenViewModel.Event.OnLimitsChanged(limits))(state)
        assertEquals(limits, updated.limits)
    }

    @Test
    fun `updateStateForEvent UpdateLoadingState updates generatingBill`() {
        val state = CashScreenViewModel.State()
        val updated = updateStateForEvent(
            CashScreenViewModel.Event.UpdateLoadingState(loading = true, success = false)
        )(state)
        assertTrue(updated.generatingBill.loading)
        assertFalse(updated.generatingBill.success)
    }

    // ---------------------------------------------------------------
    // State computed property tests
    // ---------------------------------------------------------------

    @Test
    fun `maxAvailableForGive formats from maxForGive pair`() {
        val state = CashScreenViewModel.State(
            maxForGive = 42.50 to CurrencyCode.USD,
        )
        // Fiat(42.50, USD).formatted() returns something like "$42.50"
        val result = state.maxAvailableForGive
        assertTrue(result.isNotEmpty(), "maxAvailableForGive should not be empty when maxForGive is set")
        assertTrue(result.contains("42"), "maxAvailableForGive should contain the amount")
    }

    // ---------------------------------------------------------------
    // ViewModel integration tests (require mocking)
    // ---------------------------------------------------------------

    @Test
    fun `checkBalanceLimit returns true and shows alert when over balance`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            // Token with $10 balance
            val token: Token = mockk(relaxed = true)
            val balance = LocalFiat(
                underlyingTokenAmount = Fiat(10.0, CurrencyCode.USD),
                nativeAmount = Fiat(10.0, CurrencyCode.USD),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            )
            val tokenWithBalance = TokenWithLocalizedBalance(token = token, balance = balance)

            val vm = createViewModel()

            // Set state: token + currencyModel with USD
            vm.dispatchEvent(CashScreenViewModel.Event.OnTokenUpdated(tokenWithBalance))
            vm.dispatchEvent(
                CashScreenViewModel.Event.OnCurrencyChanged(
                    Currency(code = "USD", name = "US Dollar")
                )
            )
            // Enter amount via delegate: $20 (over the $10 balance)
            vm.amountDelegate.onCurrencyChanged(Currency(code = "USD", name = "US Dollar"))
            vm.amountDelegate.onNumber(2)
            vm.amountDelegate.onNumber(0)
            advanceUntilIdle()

            val result = vm.checkBalanceLimit()
            assertTrue(result, "checkBalanceLimit should return true when amount exceeds balance")
            assertTrue(
                BottomBarManager.messages.value.any { it.title == "error_title_youNeedMoreCash" },
                "Should show youNeedMoreCash alert"
            )
        }

    @Test
    fun `checkBalanceLimit returns false when within balance`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val token: Token = mockk(relaxed = true)
            val balance = LocalFiat(
                underlyingTokenAmount = Fiat(100.0, CurrencyCode.USD),
                nativeAmount = Fiat(100.0, CurrencyCode.USD),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            )
            val tokenWithBalance = TokenWithLocalizedBalance(token = token, balance = balance)

            val vm = createViewModel()

            vm.dispatchEvent(CashScreenViewModel.Event.OnTokenUpdated(tokenWithBalance))
            vm.dispatchEvent(
                CashScreenViewModel.Event.OnCurrencyChanged(
                    Currency(code = "USD", name = "US Dollar")
                )
            )
            // Enter amount via delegate: $5 (within the $100 balance)
            vm.amountDelegate.onCurrencyChanged(Currency(code = "USD", name = "US Dollar"))
            vm.amountDelegate.onNumber(5)
            advanceUntilIdle()

            val result = vm.checkBalanceLimit()
            assertFalse(result, "checkBalanceLimit should return false when amount is within balance")
        }

    @Test
    fun `checkSendLimit returns true and shows error when over limit`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val limits = Limits(
                sinceDate = 0L,
                fetchDate = System.currentTimeMillis(),
                sendLimits = mapOf(CurrencyCode.USD to SendLimit(50.0, 500.0, 1000.0)),
                amountUsdTransactedSinceConsumption = Fiat.Zero,
            )

            val vm = createViewModel()

            vm.dispatchEvent(CashScreenViewModel.Event.OnLimitsChanged(limits))
            vm.dispatchEvent(
                CashScreenViewModel.Event.OnCurrencyChanged(
                    Currency(code = "USD", name = "US Dollar")
                )
            )
            // Enter amount via delegate: $100 (over $50 limit)
            vm.amountDelegate.onCurrencyChanged(Currency(code = "USD", name = "US Dollar"))
            vm.amountDelegate.onNumber(1)
            vm.amountDelegate.onNumber(0)
            vm.amountDelegate.onNumber(0)
            advanceUntilIdle()

            val result = vm.checkSendLimit()
            assertTrue(result, "checkSendLimit should return true when amount exceeds send limit")
            assertTrue(
                BottomBarManager.messages.value.any { it.title == "error_title_sendLimitReached" },
                "Should show sendLimitReached error"
            )
        }

    @Test
    fun `checkSendLimit returns false when within limit`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val limits = Limits(
                sinceDate = 0L,
                fetchDate = System.currentTimeMillis(),
                sendLimits = mapOf(CurrencyCode.USD to SendLimit(500.0, 500.0, 1000.0)),
                amountUsdTransactedSinceConsumption = Fiat.Zero,
            )

            val vm = createViewModel()

            vm.dispatchEvent(CashScreenViewModel.Event.OnLimitsChanged(limits))
            vm.dispatchEvent(
                CashScreenViewModel.Event.OnCurrencyChanged(
                    Currency(code = "USD", name = "US Dollar")
                )
            )
            // Enter amount via delegate: $10 (within $500 limit)
            vm.amountDelegate.onCurrencyChanged(Currency(code = "USD", name = "US Dollar"))
            vm.amountDelegate.onNumber(1)
            vm.amountDelegate.onNumber(0)
            advanceUntilIdle()

            val result = vm.checkSendLimit()
            assertFalse(result, "checkSendLimit should return false when amount is within limit")
        }
}
