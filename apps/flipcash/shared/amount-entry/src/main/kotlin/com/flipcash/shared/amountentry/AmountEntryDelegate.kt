package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.CurrencyHolder
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : AmountEntryController {
    constructor(
        exchange: Exchange,
        scope: CoroutineScope,
        maxLength: Int = 10,
        style: AmountEntryStyle,
        loadingState: StateFlow<LoadingSuccessState> = MutableStateFlow(LoadingSuccessState()),
        maxAmount: StateFlow<Fiat?> = MutableStateFlow(null),
        minimumAmount: StateFlow<Fiat?> = MutableStateFlow(null),
    ) : this(exchange, scope, maxLength, MutableStateFlow(style), loadingState, maxAmount, minimumAmount)

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

    override val config: StateFlow<AmountEntryConfig> = combine(
        _state, style, loadingState, maxAmount, minimumAmount,
    ) { delegateState, currentStyle, loading, max, min ->
        val isBelowMin = min != null && currentStyle.belowMinHint != null &&
            !delegateState.isEmpty && delegateState.enteredAmount > 0 &&
            Fiat(delegateState.enteredAmount, min.currencyCode).valueLessThan(min)

        val isOverMax = max != null && !delegateState.isEmpty &&
            Fiat(delegateState.enteredAmount, max.currencyCode).valueGreaterThan(max)

        val hint = when {
            isBelowMin -> AmountEntryHint.Error(currentStyle.belowMinHint!!(min!!.formatted()))
            isOverMax -> AmountEntryHint.Error(currentStyle.overMaxHint(max!!.formatted()))
            max != null -> AmountEntryHint.Info(currentStyle.infoHint(max.formatted()))
            else -> AmountEntryHint.None
        }

        AmountEntryConfig(
            hint = hint,
            canConfirm = delegateState.enteredAmount > 0.0,
            canChangeCurrency = currentStyle.canChangeCurrency,
            action = AmountEntryAction(
                label = currentStyle.actionLabel,
                style = currentStyle.actionStyle,
                loadingState = loading,
            ),
        )
    }.stateIn(
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

        exchange.observePreferredRate()
            .onEach {
                numberInputHelper.reset()
                _state.update { s ->
                    s.copy(amountAnimatedModel = AmountAnimatedInputUiModel())
                }
            }.launchIn(scope)
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
