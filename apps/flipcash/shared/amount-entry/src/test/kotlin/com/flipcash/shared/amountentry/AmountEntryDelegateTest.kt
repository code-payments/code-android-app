package com.flipcash.shared.amountentry

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.ui.ConfirmationStyle
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.view.LoadingSuccessState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AmountEntryDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val exchange: Exchange = mockk(relaxed = true) {
        every { observePreferredRate() } returns emptyFlow()
    }

    private val usd = Currency(code = "USD", name = "US Dollar", symbol = "$")

    /**
     * Creates a delegate using an [UnconfinedTestDispatcher] scope that shares the
     * test's scheduler. This ensures `stateIn(WhileSubscribed)` dispatches eagerly
     * while NOT blocking `runTest` completion (unlike `this` / TestScope).
     */
    private fun TestScope.createDelegate(
        style: AmountEntryStyle = AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Next")),
        loadingState: MutableStateFlow<LoadingSuccessState> = MutableStateFlow(LoadingSuccessState()),
        maxAmount: MutableStateFlow<Fiat?> = MutableStateFlow(null),
        minimumAmount: MutableStateFlow<Fiat?> = MutableStateFlow(null),
    ): AmountEntryDelegate {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val delegate = AmountEntryDelegate(
            exchange = exchange,
            scope = scope,
            style = style,
            loadingState = loadingState,
            maxAmount = maxAmount,
            minimumAmount = minimumAmount,
        )
        // Keep a subscriber active so WhileSubscribed produces values
        delegate.config.launchIn(scope)
        return delegate
    }

    // ---------------------------------------------------------------
    // Keypad input
    // ---------------------------------------------------------------

    @Test
    fun `default state has zero amount`() = runTest {
        val delegate = createDelegate()
        assertEquals(0.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `entering a single digit updates amount`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)
        assertEquals(5.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `entering multiple digits builds the number`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(1)
        delegate.onNumber(2)
        delegate.onNumber(3)
        assertEquals(123.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `entering decimal and digits builds fractional amount`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(1)
        delegate.onDecimal()
        delegate.onNumber(5)
        delegate.onNumber(0)
        assertEquals(1.50, delegate.state.value.enteredAmount)
    }

    @Test
    fun `backspace removes last digit`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(4)
        delegate.onNumber(2)
        delegate.onBackspace()
        assertEquals(4.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `backspace on single digit returns to zero`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(7)
        delegate.onBackspace()
        assertEquals(0.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `reset clears amount`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(9)
        delegate.onNumber(9)
        delegate.reset()
        assertEquals(0.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `onCurrencyChanged updates currency in state`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        assertEquals(CurrencyCode.USD, delegate.state.value.currency.code)
    }

    // ---------------------------------------------------------------
    // Prefill
    // ---------------------------------------------------------------

    @Test
    fun `prefill whole number`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.prefill(42.0)
        assertEquals(42.0, delegate.state.value.enteredAmount)
    }

    @Test
    fun `prefill fractional amount`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.prefill(3.75)
        assertEquals(3.75, delegate.state.value.enteredAmount)
    }

    @Test
    fun `prefill sub-dollar amount`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.prefill(0.50)
        assertEquals(0.50, delegate.state.value.enteredAmount)
    }

    // ---------------------------------------------------------------
    // Config derivation — default / initial state
    // ---------------------------------------------------------------

    @Test
    fun `config initial state uses style label and defaults`() = runTest {
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Send"),
                actionStyle = ConfirmationStyle.Slide,
            ),
        )

        val config = delegate.config.value
        assertEquals("Send", config.action.label.text)
        assertEquals(ConfirmationStyle.Slide, config.action.style)
        assertFalse(config.canConfirm)
        assertTrue(config.canChangeCurrency)
    }

    @Test
    fun `config canConfirm is true when amount entered`() = runTest {
        val delegate = createDelegate()
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)

        assertTrue(delegate.config.value.canConfirm)
    }

    @Test
    fun `config canConfirm is false when amount is zero`() = runTest {
        val delegate = createDelegate()

        assertFalse(delegate.config.value.canConfirm)
    }

    // ---------------------------------------------------------------
    // Config derivation — canChangeCurrency
    // ---------------------------------------------------------------

    @Test
    fun `config canChangeCurrency reflects style`() = runTest {
        val delegate = createDelegate(
            style = AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Buy"), canChangeCurrency = false),
        )

        assertFalse(delegate.config.value.canChangeCurrency)
    }

    // ---------------------------------------------------------------
    // Config derivation — hints with maxAmount
    // ---------------------------------------------------------------

    @Test
    fun `config shows info hint when max is set and amount is within`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(100.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
            ),
            maxAmount = max,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)

        val hint = delegate.config.value.hint
        assertIs<AmountEntryHint.Info>(hint)
        assertTrue(hint.text.startsWith("Up to"))
    }

    @Test
    fun `config shows error hint when amount exceeds max`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(10.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
            ),
            maxAmount = max,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)
        delegate.onNumber(0) // 50 > 10

        val hint = delegate.config.value.hint
        assertIs<AmountEntryHint.Error>(hint)
        assertTrue(hint.text.startsWith("Over"))
    }

    @Test
    fun `config shows no hint when max is null`() = runTest {
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
            ),
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)

        assertIs<AmountEntryHint.None>(delegate.config.value.hint)
    }

    @Test
    fun `config shows info hint when empty and max is set`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(100.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
            ),
            maxAmount = max,
        )

        // Amount is 0, so isOverMax is false — we get info hint
        val hint = delegate.config.value.hint
        assertIs<AmountEntryHint.Info>(hint)
    }

    // ---------------------------------------------------------------
    // Config derivation — hints with minimumAmount
    // ---------------------------------------------------------------

    @Test
    fun `config shows below-min error when amount is below minimum`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(100.0, CurrencyCode.USD))
        val min = MutableStateFlow<Fiat?>(Fiat(5.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
                belowMinHint = { "Min is $it" },
            ),
            maxAmount = max,
            minimumAmount = min,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(2) // below 5

        val hint = delegate.config.value.hint
        assertIs<AmountEntryHint.Error>(hint)
        assertTrue(hint.text.startsWith("Min is"))
    }

    @Test
    fun `config does not show below-min when belowMinHint is null`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(100.0, CurrencyCode.USD))
        val min = MutableStateFlow<Fiat?>(Fiat(5.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
                // belowMinHint is null — no below-min check
            ),
            maxAmount = max,
            minimumAmount = min,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(2) // below min but belowMinHint is null

        // Should show info, not error — below-min is disabled
        assertIs<AmountEntryHint.Info>(delegate.config.value.hint)
    }

    @Test
    fun `config amount at minimum is not considered below min`() = runTest {
        val max = MutableStateFlow<Fiat?>(Fiat(100.0, CurrencyCode.USD))
        val min = MutableStateFlow<Fiat?>(Fiat(5.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
                belowMinHint = { "Min is $it" },
            ),
            maxAmount = max,
            minimumAmount = min,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5) // exactly at minimum

        // At minimum — should NOT be below-min (uses valueLessThan, not valueLessThanOrEqualTo)
        assertIs<AmountEntryHint.Info>(delegate.config.value.hint)
    }

    @Test
    fun `config below-min takes priority over over-max`() = runTest {
        // Edge case: min=5, max=3 (unusual but tests priority)
        val max = MutableStateFlow<Fiat?>(Fiat(3.0, CurrencyCode.USD))
        val min = MutableStateFlow<Fiat?>(Fiat(5.0, CurrencyCode.USD))
        val delegate = createDelegate(
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
                belowMinHint = { "Min is $it" },
            ),
            maxAmount = max,
            minimumAmount = min,
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(4) // above max(3) but below min(5) — belowMin should win

        val hint = delegate.config.value.hint
        assertIs<AmountEntryHint.Error>(hint)
        assertTrue(hint.text.startsWith("Min is"))
    }

    // ---------------------------------------------------------------
    // Config derivation — loadingState
    // ---------------------------------------------------------------

    @Test
    fun `config action reflects loading state`() = runTest {
        val loading = MutableStateFlow(LoadingSuccessState(loading = true))
        val delegate = createDelegate(loadingState = loading)

        assertTrue(delegate.config.value.action.loadingState.loading)
        assertFalse(delegate.config.value.action.loadingState.success)
    }

    @Test
    fun `config action reflects success state`() = runTest {
        val loading = MutableStateFlow(LoadingSuccessState(success = true))
        val delegate = createDelegate(loadingState = loading)

        assertTrue(delegate.config.value.action.loadingState.success)
    }

    // ---------------------------------------------------------------
    // Config derivation — reactive style updates
    // ---------------------------------------------------------------

    @Test
    fun `config updates when style flow changes`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val styleFlow = MutableStateFlow(AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Buy")))
        val delegate = AmountEntryDelegate(
            exchange = exchange,
            scope = scope,
            style = styleFlow,
        )
        delegate.config.launchIn(scope)

        assertEquals("Buy", delegate.config.value.action.label.text)

        styleFlow.value = AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Sell"))

        assertEquals("Sell", delegate.config.value.action.label.text)
    }

    @Test
    fun `config updates when maxAmount flow changes`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val max = MutableStateFlow<Fiat?>(null)
        val delegate = AmountEntryDelegate(
            exchange = exchange,
            scope = scope,
            style = AmountEntryStyle(
                actionLabel = AmountEntryLabel.Plain("Next"),
                infoHint = { "Up to $it" },
                overMaxHint = { "Over $it" },
            ),
            maxAmount = max,
        )
        delegate.config.launchIn(scope)
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)

        // No max — no hint
        assertIs<AmountEntryHint.None>(delegate.config.value.hint)

        // Set max — info hint appears
        max.value = Fiat(100.0, CurrencyCode.USD)

        assertIs<AmountEntryHint.Info>(delegate.config.value.hint)
    }

    // ---------------------------------------------------------------
    // Max length enforcement
    // ---------------------------------------------------------------

    @Test
    fun `max length limits number of digits`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val delegate = AmountEntryDelegate(
            exchange = exchange,
            scope = scope,
            maxLength = 3,
            style = AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Next")),
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(1)
        delegate.onNumber(2)
        delegate.onNumber(3)
        delegate.onNumber(4) // should be rejected
        assertEquals(123.0, delegate.state.value.enteredAmount)
    }

    // ---------------------------------------------------------------
    // Rate change resets input
    // ---------------------------------------------------------------

    @Test
    fun `rate change resets entered amount`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val rateFlow = MutableStateFlow(Rate.oneToOne)
        val exch: Exchange = mockk(relaxed = true) {
            every { observePreferredRate() } returns rateFlow
        }
        val delegate = AmountEntryDelegate(
            exchange = exch,
            scope = scope,
            style = AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("Next")),
        )
        delegate.onCurrencyChanged(usd)
        delegate.onNumber(5)
        assertEquals(5.0, delegate.state.value.enteredAmount)

        // Simulate rate change
        rateFlow.value = Rate(1.1, CurrencyCode.EUR)
        assertEquals(0.0, delegate.state.value.enteredAmount)
    }
}
