package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.CurrencyHolder
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class AmountEntryDelegate(
    exchange: Exchange,
    scope: CoroutineScope,
    private val maxLength: Int = 10,
    style: StateFlow<AmountEntryStyle>,
    loadingState: StateFlow<LoadingSuccessState> = MutableStateFlow(LoadingSuccessState()),
    maxAmount: StateFlow<Fiat?> = MutableStateFlow(null),
    minimumAmount: StateFlow<Fiat?> = MutableStateFlow(null),
    // An extra gate on the confirm action, ANDed with "something was entered". For flows where a
    // valid amount still isn't actionable — a settings screen whose Save stays inert until the
    // entry differs from the saved value. Defaults to always-allowed.
    confirmEnabled: StateFlow<Boolean> = MutableStateFlow(true),
    // Emits whenever the selected token changes. Like a region/currency change, switching
    // tokens re-denominates the entry, so the typed amount is reset (see init). Defaults to a
    // no-op for flows without a token concept.
    tokenChanges: Flow<*> = emptyFlow<Any?>(),
) : AmountEntryController {
    constructor(
        exchange: Exchange,
        scope: CoroutineScope,
        maxLength: Int = 10,
        style: AmountEntryStyle,
        loadingState: StateFlow<LoadingSuccessState> = MutableStateFlow(LoadingSuccessState()),
        maxAmount: StateFlow<Fiat?> = MutableStateFlow(null),
        minimumAmount: StateFlow<Fiat?> = MutableStateFlow(null),
        confirmEnabled: StateFlow<Boolean> = MutableStateFlow(true),
        tokenChanges: Flow<*> = emptyFlow<Any?>(),
    ) : this(
        exchange,
        scope,
        maxLength,
        MutableStateFlow(style),
        loadingState,
        maxAmount,
        minimumAmount,
        confirmEnabled,
        tokenChanges,
    )

    data class State(
        val currency: CurrencyHolder = CurrencyHolder(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    ) {
        val enteredAmount: Double
            get() = amountAnimatedModel.amountData.amount

        val isEmpty: Boolean
            get() = amountAnimatedModel.amountData.isEmpty()
    }

    private val numberInputHelper = NumberInputHelper()
    private val _state = MutableStateFlow(State())
    override val state: StateFlow<State> = _state.asStateFlow()

    // The bounds and the confirm gate travel together because `combine` tops out at five typed
    // sources and the config already needs state, style and loading.
    private val bounds: Flow<Triple<Fiat?, Fiat?, Boolean>> =
        combine(maxAmount, minimumAmount, confirmEnabled) { max, min, allowed ->
            Triple(max, min, allowed)
        }

    override val config: StateFlow<AmountEntryConfig> = combine(
        _state, style, loadingState, bounds,
    ) { delegateState, currentStyle, loading, (max, min, confirmAllowed) ->
        val isBelowMin = min != null && currentStyle.belowMinHint != null &&
            !delegateState.isEmpty && delegateState.enteredAmount > 0 &&
            Fiat(delegateState.enteredAmount, min.currencyCode).valueLessThan(min)

        val isOverMax = max != null && !delegateState.isEmpty &&
            Fiat(delegateState.enteredAmount, max.currencyCode).valueGreaterThan(max)

        val hint = when {
            isBelowMin -> AmountEntryHint.Error(currentStyle.belowMinHint!!(min!!.formatted()))
            isOverMax -> AmountEntryHint.Error(currentStyle.overMaxHint(max!!.formatted()))
            max != null -> AmountEntryHint.Info(currentStyle.infoHint(max.formatted()))
            // No ceiling to describe, so the floor is the standing hint rather than only an
            // error — it tells the user the rule before they break it.
            min != null && currentStyle.belowMinHint != null ->
                AmountEntryHint.Info(currentStyle.belowMinHint!!(min.formatted()))
            else -> AmountEntryHint.None
        }

        AmountEntryConfig(
            hint = hint,
            canConfirm = delegateState.enteredAmount > 0.0 && confirmAllowed,
            canChangeCurrency = currentStyle.canChangeCurrency,
            action = AmountEntryAction(
                label = currentStyle.actionLabel,
                style = currentStyle.actionStyle,
                loadingState = loading,
            ),
        )
    }.scan(null as AmountEntryConfig?) { prev, current ->
        // Freeze hint and confirm state while send is in progress;
        // only the action's loadingState is allowed to update.
        val loading = current.action.loadingState
        if (prev != null && (loading.loading || loading.success)) {
            prev.copy(action = current.action)
        } else {
            current
        }
    }.filterNotNull()
    .stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        AmountEntryConfig(
            action = AmountEntryAction(
                label = style.value.actionLabel,
                style = style.value.actionStyle,
            ),
        ),
    )

    init {
        numberInputHelper.reset()

        // Reset the typed amount whenever the entry is re-denominated: a preferred
        // currency/region change (rate) or a selected-token change. `drop(1)` on the token
        // stream skips its initial value so an in-flight prefill isn't wiped on construction.
        merge(
            exchange.observePreferredRate(),
            tokenChanges.distinctUntilChanged().drop(1),
        ).onEach { reset() }.launchIn(scope)
    }

    fun onCurrencyChanged(currency: Currency) {
        numberInputHelper.fractionUnits = currency.fractionUnits
        _state.update { it.copy(currency = CurrencyHolder(currency)) }
    }

    override fun onNumber(number: Int) {
        numberInputHelper.fractionUnits = _state.value.currency.fractionUnits
        numberInputHelper.maxLength = maxLength
        numberInputHelper.onNumber(number)
        updateAnimatedModel(backspace = false)
    }

    override fun onDecimal() {
        numberInputHelper.onDot()
        updateAnimatedModel(backspace = false)
    }

    override fun onBackspace() {
        numberInputHelper.onBackspace()
        updateAnimatedModel(backspace = true)
    }

    fun prefill(amount: Double) {
        numberInputHelper.maxLength = maxLength
        val scale = pow10(numberInputHelper.fractionUnits)
        val smallest = (amount * scale).roundToInt()
        val whole = smallest / scale
        val frac = smallest % scale

        if (whole == 0L) {
            numberInputHelper.onNumber(0)
        } else {
            var div = 1L
            while (div * 10 <= whole) div *= 10
            var n = whole
            while (div > 0) {
                numberInputHelper.onNumber((n / div).toInt())
                n %= div
                div /= 10
            }
        }

        if (frac > 0) {
            numberInputHelper.onDot()
            var div = scale / 10
            var n = frac
            while (div > 0) {
                numberInputHelper.onNumber((n / div).toInt())
                n %= div
                if (n == 0L) break
                div /= 10
            }
        }

        updateAnimatedModel(backspace = false)
    }

    /**
     * Replaces whatever is currently entered with [amount], as opposed to [prefill], which types
     * on top of the existing entry. For corrections the flow makes on the user's behalf — dropping
     * an entry to the maximum a balance can actually fund, say — so the amount screen keeps
     * agreeing with what the rest of the flow priced.
     */
    fun setAmount(amount: Double) {
        numberInputHelper.fractionUnits = _state.value.currency.fractionUnits
        reset()
        prefill(amount)
    }

    fun reset() {
        numberInputHelper.reset()
        _state.update { it.copy(amountAnimatedModel = AmountAnimatedInputUiModel()) }
    }

    private fun updateAnimatedModel(backspace: Boolean) {
        val current = _state.value.amountAnimatedModel
        val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

        val updated = current.copy(
            amountDataLast = current.amountData,
            amountData = amount,
            lastPressedBackspace = backspace,
        )
        _state.update { it.copy(amountAnimatedModel = updated) }
    }

    companion object {
        private fun pow10(exp: Int): Long {
            var result = 1L
            repeat(exp) { result *= 10 }
            return result
        }
    }
}
