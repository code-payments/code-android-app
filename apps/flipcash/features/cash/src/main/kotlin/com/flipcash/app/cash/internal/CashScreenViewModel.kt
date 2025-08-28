package com.flipcash.app.cash.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.features.cash.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.minus
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
internal class CashScreenViewModel @Inject constructor(
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    balanceController: BalanceController,
    transactionController: TransactionController,
    onrampController: OnRampAmountController,
) : BaseViewModel2<CashScreenViewModel.State, CashScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    private val numberInputHelper = NumberInputHelper()

    internal data class State(
        val balance: LocalFiat = LocalFiat.Zero,
        val currencyModel: CurrencyHolder = CurrencyHolder(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val limits: Limits? = null,
        val maxForGive: Pair<Double, CurrencyCode>? = null,
        val generatingBill: LoadingSuccessState = LoadingSuccessState(),
        val onrampProviders: List<OnRampProvider> = emptyList(),
        ) {
        val canGive: Boolean
            get() = (amountAnimatedModel.amountData.amount.toDoubleOrNull() ?: 0.0) > 0.00

        val maxAvailableForGive: String
            get() = maxForGive?.let { Fiat(it.first, it.second).formatted() }.orEmpty()

        val isError: Boolean
            get() {
                if (amountAnimatedModel.amountData.amount.isEmpty()) return false

                if (maxForGive != null) {
                    if ((amountAnimatedModel.amountData.amount.toDoubleOrNull()
                            ?: 0.0) <= maxForGive.first
                    ) {
                        return false
                    }
                }

                return true
            }
    }

    sealed interface Event {
        data class OnBalanceChanged(val balance: LocalFiat) : Event
        data class OnNumberPressed(val number: Int) : Event
        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnAmountChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) : Event
        data class OnCurrencyChanged(val model: Currency) : Event
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) : Event
        data class OnLimitsChanged(val limits: Limits?) : Event
        data object OnGive : Event
        data class PresentBill(val bill: Bill.Cash) : Event

        data class OnOnRampProvidersChanged(val providers: List<OnRampProvider>): Event

        data class AddCashToWallet(val amount: Fiat) : Event
        data class OpenOnRampAmountModal(val amount: Fiat) : Event
        data class UpdateLoadingState(val loading: Boolean = false, val success: Boolean = false) :
            Event

        data class OpenScreen(val screen: NavScreenProvider) : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount.toDoubleOrNull() ?: 0.0
        val conversionRate = exchange.rateToUsd(stateFlow.value.currencyModel.code ?: CurrencyCode.USD) ?: Rate.ignore
        val enteredInUsdc = Fiat(
            fiat = amount,
            currencyCode = stateFlow.value.currencyModel.code ?: CurrencyCode.USD
        ).convertingTo(conversionRate)
        val balanceInUsdc = stateFlow.value.balance.usdc

        val isOverBalance = enteredInUsdc > balanceInUsdc
        if (isOverBalance || conversionRate == Rate.ignore) {
            BottomBarManager.showMessage(
                resources.getString(R.string.error_title_youNeedMoreCash),
                resources.getString(R.string.error_description_youNeedMoreCash),
                type = BottomBarManager.BottomBarMessageType.ERROR,
                showScrim = true,
                showCancel = false,
                actions = listOf(
                    BottomBarAction(
                        text = resources.getString(R.string.action_addMoreCash),
                        style = BottomBarManager.BottomBarButtonStyle.Filled,
                    ) {
                        viewModelScope.launch {
                            val rate = exchange.entryRate
                            // if we are USD we can skip the rate fetch since its 1:1
                            if (rate.currency != CurrencyCode.USD) {
                                exchange.fetchRatesIfNeeded()
                            }

                            val localizedAmount = Fiat(amount, rate.currency)

                            val amountFiat = LocalFiat(
                                usdc = localizedAmount.convertingTo(exchange.rateToUsd(rate.currency)!!),
                                converted = localizedAmount,
                                rate = rate,
                            )

                            val neededAmount = amountFiat.usdc - balanceInUsdc

                            dispatchEvent(Event.AddCashToWallet(neededAmount))
                        }
                    },
                    BottomBarAction(
                        text = resources.getString(R.string.action_dismiss),
                        style = BottomBarManager.BottomBarButtonStyle.Text,
                    )
                )
            )
        }
        isOverBalance
    }
    val checkSendLimit: () -> Boolean = {
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount.toDoubleOrNull() ?: 0.0
        val currency = stateFlow.value.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.limits?.sendLimitFor(it) } ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showError(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached)
            )
        }
        isOverLimit
    }

    init {
        numberInputHelper.reset()

        viewModelScope.launch(Dispatchers.IO) {
            exchange.fetchRatesIfNeeded()
        }

        combine(
            balanceController.rawBalance,
            exchange.observeEntryRate(),
        ) { balance, rate ->
            LocalFiat(
                usdc = balance,
                converted = balance.convertingTo(rate),
                rate = rate
            )
        }.onEach {
            dispatchEvent(Event.OnBalanceChanged(it))
        }.mapNotNull {
            exchange.getCurrency(it.rate.currency.name)
        }.onEach {
            dispatchEvent(Event.OnCurrencyChanged(it))
        }.launchIn(viewModelScope)

        exchange.observeEntryRate()
            .onEach {
                // reset when entry rate changes
                numberInputHelper.reset()
                dispatchEvent(Event.OnAmountChanged(AmountAnimatedInputUiModel()))
            }.launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.OnLimitsChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCurrencyChanged>()
            .map { it.model }
            .onEach {
                numberInputHelper.fractionUnits = it.fractionUnits
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnNumberPressed>()
            .map { it.number }
            .onEach { number ->
                numberInputHelper.fractionUnits = stateFlow.value.currencyModel.fractionUnits
                numberInputHelper.maxLength = 10 // 1 billion dollars
                numberInputHelper.onNumber(number)
                dispatchEvent(Event.OnEnteredNumberChanged())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDecimalPressed>()
            .onEach {
                numberInputHelper.onDot()
                dispatchEvent(Event.OnEnteredNumberChanged())
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBackspace>()
            .onEach {
                numberInputHelper.onBackspace()
                dispatchEvent(Event.OnEnteredNumberChanged(true))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnEnteredNumberChanged>()
            .map { it.backspace }
            .onEach { backspace ->
                val current = stateFlow.value.amountAnimatedModel
                val model = stateFlow.value.amountAnimatedModel
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnAmountChanged(updated))
            }.launchIn(viewModelScope)

        stateFlow
            .filter { it.limits != null }
            .map { it.limits to it.balance }
            .onEach { (limits, balance) ->
                val sendLimit = limits?.sendLimitFor(balance.rate.currency) ?: SendLimit.Zero
                val nextTransactionLimit = sendLimit.nextTransaction
                val max = min(nextTransactionLimit, balance.converted.doubleValue)
                dispatchEvent(Event.OnMaxDetermined(max, balance.rate.currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnGive>()
            .map { stateFlow.value.amountAnimatedModel }
            .filter { !(checkBalanceLimit() || checkSendLimit()) }
            .onEach { data ->
                dispatchEvent(Event.UpdateLoadingState(loading = true))
                val rate = exchange.entryRate
                // if we are USD we can skip the rate fetch since its 1:1
                if (rate.currency != CurrencyCode.USD) {
                    exchange.fetchRatesIfNeeded()
                }

                val localizedAmount = Fiat(data.amountData.amount, rate.currency)

                val amountFiat = LocalFiat(
                    usdc = localizedAmount.convertingTo(exchange.rateToUsd(rate.currency)!!),
                    converted = localizedAmount,
                    rate = rate,
                )

                val bill = Bill.Cash(amount = amountFiat)
                dispatchEvent(Event.UpdateLoadingState(loading = false, success = true))
                dispatchEvent(Event.PresentBill(bill))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.AddCashToWallet>()
            .map { it.amount }
            .onEach { amount ->
                val providers = stateFlow.value.onrampProviders
                if (providers.any { it is OnRampProvider.Coinbase }) {
                    // has coinbase provider - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal(amount))
                } else {
                    // route to provider list
                    dispatchEvent(Event.OpenScreen(NavScreenProvider.HomeScreen.OnRamp.ProviderList(NavScreenProvider.HomeScreen.Cash, amount)))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OpenOnRampAmountModal>()
            .map { onrampController.requestAmountSelection(OnRampProvider.Coinbase(OnRampType.Virtual)) }
            .flatMapLatest {
                onrampController.confirmationEvents.take(1)
            }.onEach { event ->
                when (event) {
                    is ConfirmationEvent.OnConfirmationSuccess -> {
                        when (event.amount) {
                            OnRampAmount.Custom -> dispatchEvent(Event.OpenScreen(NavScreenProvider.HomeScreen.OnRamp.Amount))
                            is OnRampAmount.Predefined -> Unit
                        }
                    }

                    ConfirmationEvent.Cancelled -> Unit
                }
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBalanceChanged -> { state ->
                    state.copy(balance = event.balance)
                }

                is Event.OnAmountChanged -> { state ->
                    state.copy(
                        amountAnimatedModel = event.amountAnimatedModel
                    )
                }

                is Event.OnOnRampProvidersChanged -> { state ->
                    state.copy(onrampProviders = event.providers)
                }

                is Event.OpenOnRampAmountModal -> { state -> state }

                is Event.OpenScreen -> { state -> state }

                Event.OnBackspace,
                Event.OnGive,
                is Event.OnEnteredNumberChanged,
                is Event.PresentBill,
                is Event.OnNumberPressed,
                Event.OnDecimalPressed -> { state -> state }

                is Event.OnCurrencyChanged -> { state ->
                    state.copy(currencyModel = CurrencyHolder(event.model))
                }

                is Event.UpdateLoadingState -> { state ->
                    val loadingSuccess = state.generatingBill
                    state.copy(
                        generatingBill = loadingSuccess.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.AddCashToWallet -> { state -> state }
                is Event.OnMaxDetermined -> { state ->
                    state.copy(maxForGive = event.max to event.currencyCode)
                }

                is Event.OnLimitsChanged -> { state -> state.copy(limits = event.limits) }
            }
        }
    }
}