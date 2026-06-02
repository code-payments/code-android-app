package com.flipcash.app.directsend.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.directsend.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.min

/**
 * Reusable ViewModel for amount entry with keypad input, currency selection,
 * and limit validation. Emits [Event.AmountConfirmed] when the user confirms
 * a valid amount. The caller is responsible for executing the transaction.
 */
@HiltViewModel
internal class AmountEntryViewModel @Inject constructor(
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
) : BaseViewModel<AmountEntryViewModel.State, AmountEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val token: Token? = null,
        val currencyModel: CurrencyHolder = CurrencyHolder(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val limits: Limits? = null,
        val maxSend: Pair<Double, CurrencyCode>? = null,
    ) {
        val canSend: Boolean
            get() = amountAnimatedModel.amountData.amount > 0.0

        val isError: Boolean
            get() {
                if (amountAnimatedModel.amountData.isEmpty()) return false
                if (maxSend != null) {
                    val enteredAmount = Fiat(
                        fiat = amountAnimatedModel.amountData.amount,
                        currencyCode = maxSend.second
                    )
                    val limit = Fiat(maxSend.first, maxSend.second)
                    if (enteredAmount.valueLessThanOrEqualTo(limit)) {
                        return false
                    }
                }

                return true
            }

        val maxSendFormatted: String
            get() = maxSend?.let { Fiat(it.first, it.second).formatted() }.orEmpty()
    }

    sealed interface Event {
        data class TokenUpdated(val token: Token) : Event
        data class CurrencyChanged(val currency: CurrencyHolder) : Event

        data class OnNumberPressed(val number: Int) : Event
        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnAmountChanged(val model: AmountAnimatedInputUiModel) : Event

        data class LimitsChanged(val limits: Limits?) : Event
        data class MaxSendDetermined(val max: Double, val currencyCode: CurrencyCode) : Event

        data object OnConfirmRequested : Event
        data class AmountConfirmed(val amount: Fiat, val token: Token) : Event
    }

    init {
        numberInputHelper.reset()

        tokenCoordinator.observeSelectedTokenMint()
            .flatMapLatest { mint ->
                tokenCoordinator.tokenBalances.map { tokens ->
                    tokens.find { it.token.address == mint }
                }
            }
            .filterNotNull()
            .onEach { tokenWithBalance ->
                dispatchEvent(Event.TokenUpdated(tokenWithBalance.token))
            }.launchIn(viewModelScope)

        exchange.observePreferredRate()
            .onEach { rate ->
                val currency = exchange.getCurrency(rate.currency.name)
                if (currency != null) {
                    dispatchEvent(Event.CurrencyChanged(CurrencyHolder(currency)))
                }
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CurrencyChanged>()
            .onEach { event ->
                numberInputHelper.fractionUnits = event.currency.fractionUnits
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnNumberPressed>()
            .onEach { event ->
                numberInputHelper.fractionUnits =
                    stateFlow.value.currencyModel.fractionUnits
                numberInputHelper.maxLength = 10
                numberInputHelper.onNumber(event.number)
                dispatchEvent(Event.OnEnteredNumberChanged())
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnDecimalPressed>()
            .onEach {
                numberInputHelper.onDot()
                dispatchEvent(Event.OnEnteredNumberChanged())
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnBackspace>()
            .onEach {
                numberInputHelper.onBackspace()
                dispatchEvent(Event.OnEnteredNumberChanged(backspace = true))
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnEnteredNumberChanged>()
            .onEach { event ->
                val current = stateFlow.value.amountAnimatedModel
                val amount =
                    numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = current.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = event.backspace,
                )
                dispatchEvent(Event.OnAmountChanged(updated))
            }.launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.LimitsChanged(it)) }
            .launchIn(viewModelScope)

        combine(
            transactionController.limits,
            tokenCoordinator.observeSelectedTokenMint()
                .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
            exchange.observePreferredRate(),
        ) { limits, balance, rate ->
            val balanceInLocal = balance.convertingTo(rate)
            val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
            val max = min(sendLimit.nextTransaction, balanceInLocal.toDouble())
            Event.MaxSendDetermined(max, rate.currency)
        }.onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnConfirmRequested>()
            .onEach { onConfirmRequested() }
            .launchIn(viewModelScope)
    }

    private fun checkBalanceLimit(): Boolean {
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount
        val max = stateFlow.value.maxSend ?: return false
        val entered = Fiat(amount, max.second)
        val balance = tokenCoordinator.balanceForToken(stateFlow.value.token ?: return false)
        val balanceInLocal = balance.convertingTo(exchange.preferredRate)
        val isOverBalance = entered.valueGreaterThan(balanceInLocal)
        if (isOverBalance) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_insufficientFunds),
                resources.getString(R.string.error_description_insufficientFunds),
            )
        }
        return isOverBalance
    }

    private fun checkSendLimit(): Boolean {
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount
        val currency = stateFlow.value.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.limits?.sendLimitFor(it) } ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached),
            )
        }
        return isOverLimit
    }

    private fun onConfirmRequested() {
        if (checkBalanceLimit() || checkSendLimit()) return

        val enteredAmount = numberInputHelper.amount
        if (enteredAmount <= 0) return

        val token = stateFlow.value.token ?: return
        val rate = exchange.preferredRate
        dispatchEvent(Event.AmountConfirmed(Fiat(enteredAmount, rate.currency), token))
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.TokenUpdated -> { state ->
                    state.copy(token = event.token)
                }
                is Event.CurrencyChanged -> { state ->
                    state.copy(currencyModel = event.currency)
                }
                is Event.OnAmountChanged -> { state ->
                    state.copy(amountAnimatedModel = event.model)
                }
                is Event.LimitsChanged -> { state ->
                    state.copy(limits = event.limits)
                }
                is Event.MaxSendDetermined -> { state ->
                    state.copy(maxSend = event.max to event.currencyCode)
                }
                is Event.OnNumberPressed,
                is Event.OnDecimalPressed,
                is Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnConfirmRequested,
                is Event.AmountConfirmed -> { state -> state }
            }
        }
    }
}
