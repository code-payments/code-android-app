package com.flipcash.app.pools.internal.create

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.features.pools.R
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.controllers.PoolController
import com.getcode.ed25519.Ed25519
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.replaceParam
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

internal data class NameEntryState(
    val textFieldState: TextFieldState = TextFieldState(),
    val selectedName: String = "",
) {
    val canUseName: Boolean
        get() = textFieldState.text.isNotEmpty()
}

internal data class BidEntryState(
    val balance: LocalFiat = LocalFiat.Zero,
    val limits: Limits? = null,
    val maxForBid: Pair<Double, CurrencyCode>? = null,
    val currencyModel: CurrencyHolder = CurrencyHolder(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val confirmingAmount: LoadingSuccessState = LoadingSuccessState(),
    val selectedAmount: Fiat = Fiat.Zero,
) {
    val canSet: Boolean
        get() = (amountAnimatedModel.amountData.amount.toDoubleOrNull()
            ?: 0.0) > 0.00

    val maxAvailableForBid: String
        get() = maxForBid?.let { Fiat(it.first, it.second).formatted() }.orEmpty()

    val isError: Boolean
        get() {
            if (amountAnimatedModel.amountData.amount.isEmpty()) return false

            if (maxForBid != null) {
                if ((amountAnimatedModel.amountData.amount.toDoubleOrNull()
                        ?: 0.0) <= maxForBid.first
                ) {
                    return false
                }
            }

            return true
        }
}

@HiltViewModel
internal class PoolCreateViewModel @Inject constructor(
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    balanceController: BalanceController,
    analytics: FlipcashAnalyticsService,
    transactionController: TransactionController,
    poolCoordinator: PoolsCoordinator,
) : BaseViewModel2<PoolCreateViewModel.State, PoolCreateViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    private val numberInputHelper = NumberInputHelper()

    data class State(
        val nameEntryState: NameEntryState = NameEntryState(),
        val bidEntryState: BidEntryState = BidEntryState(),
        val creatingPool: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        // name
        data object OnNameConfirmed : Event

        // amount
        data class OnBalanceChanged(val balance: LocalFiat) : Event
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) :
            Event

        data class OnLimitsChanged(val limits: Limits?) :
            Event

        data class OnNumberPressed(val number: Int) : Event
        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnAmountChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) :
            Event

        data class OnCurrencyChanged(val currency: Currency) :
            Event

        data object OnAmountConfirmed : Event
        data class UpdateConfirmingAmountState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event
        data class OnAmountAccepted(val amount: Fiat) :
            Event

        data object OnCreatePool: Event
        data class UpdateCreatingState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event
        data class OnPoolCreated(val poolId: ID): Event
    }

    val checkBidLimit: () -> Boolean = {
        val amount =
            stateFlow.value.bidEntryState.amountAnimatedModel.amountData.amount.toDoubleOrNull()
                ?: 0.0
        val currency = stateFlow.value.bidEntryState.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.bidEntryState.limits?.sendLimitFor(it) }
                ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showError(
                resources.getString(R.string.error_title_bidLimitReached),
                resources.getString(R.string.error_description_bidLimitReached)
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
            .map { it.currency }
            .onEach {
                numberInputHelper.fractionUnits = it.fractionUnits
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnNumberPressed>()
            .map { it.number }
            .onEach { number ->
                numberInputHelper.fractionUnits = stateFlow.value.bidEntryState.currencyModel.fractionUnits
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
                val current = stateFlow.value.bidEntryState.amountAnimatedModel
                val model = stateFlow.value.bidEntryState.amountAnimatedModel
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnAmountChanged(updated))
            }.launchIn(viewModelScope)

        stateFlow
            .map { it.bidEntryState }
            .filter { it.limits != null }
            .map { it.limits to it.balance }
            .onEach { (limits, balance) ->
                val sendLimit = limits?.sendLimitFor(balance.rate.currency) ?: SendLimit.Zero
                val nextTransactionLimit = sendLimit.nextTransaction
                dispatchEvent(Event.OnMaxDetermined(nextTransactionLimit, balance.rate.currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { stateFlow.value.bidEntryState.amountAnimatedModel }
            .filter { !(checkBidLimit()) }
            .onEach { data ->
                dispatchEvent(Event.UpdateConfirmingAmountState(loading = true))
                val rate = exchange.entryRate
                // if we are USD we can skip the rate fetch since its 1:1
                if (rate.currency != CurrencyCode.USD) {
                    exchange.fetchRatesIfNeeded()
                }

                val localizedAmount = Fiat(data.amountData.amount, rate.currency)

                dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = true))
                dispatchEvent(Event.OnAmountAccepted(localizedAmount))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountAccepted>()
            .onEach {
                delay(1.seconds)
                dispatchEvent(Event.UpdateConfirmingAmountState(success = false))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCreatePool>()
            .mapNotNull {
                dispatchEvent(Event.UpdateCreatingState(loading = true))
                val name = stateFlow.value.nameEntryState.selectedName
                val buyIn = stateFlow.value.bidEntryState.selectedAmount

                if (name.isEmpty() || buyIn == Fiat.Zero) {
                    dispatchEvent(Event.UpdateCreatingState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_createPoolFailed),
                        message = resources.getString(R.string.error_description_createPoolFailed)
                    )
                    return@mapNotNull null
                }
                poolCoordinator.createPool(name, buyIn)
            }.onResult(
                onError = {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_createPoolFailed),
                        message = resources.getString(R.string.error_description_createPoolFailed)
                    )
                    dispatchEvent(Event.UpdateCreatingState(loading = false))
                },
                onSuccess = {
                    dispatchEvent(Event.UpdateCreatingState(loading = false, success = true))
                    analytics.poolCreated(it)
                    dispatchEvent(Event.OnPoolCreated(it))
                }
            ).launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMaxDetermined -> { state ->
                    state.copy(
                        bidEntryState = state.bidEntryState.copy(
                            maxForBid = event.max to event.currencyCode
                        ),
                    )
                }

                is Event.OnLimitsChanged -> { state ->
                    state.copy(
                        bidEntryState = state.bidEntryState.copy(
                            limits = event.limits

                        )
                    )
                }

                is Event.OnBalanceChanged -> { state ->
                    state.copy(
                        bidEntryState = state.bidEntryState.copy(balance = event.balance)
                    )
                }

                is Event.OnAmountChanged -> { state ->
                    state.copy(bidEntryState = state.bidEntryState.copy(amountAnimatedModel = event.amountAnimatedModel))
                }

                is Event.OnCurrencyChanged -> { state ->
                    state.copy(
                        bidEntryState = state.bidEntryState.copy(
                            currencyModel = CurrencyHolder(
                                event.currency
                            )
                        )
                    )
                }

                is Event.UpdateConfirmingAmountState -> { state ->
                    val entryState = state.bidEntryState
                    val loadingSuccess = entryState.confirmingAmount
                    state.copy(
                        bidEntryState = entryState.copy(
                            confirmingAmount = loadingSuccess.copy(
                                loading = event.loading,
                                success = event.success
                            )
                        )
                    )
                }

                is Event.OnAmountAccepted -> { state ->
                    state.copy(
                        bidEntryState = state.bidEntryState.copy(
                            selectedAmount = event.amount
                        )
                    )
                }

                Event.OnNameConfirmed -> { state ->
                    state.copy(
                        nameEntryState = state.nameEntryState.copy(
                            selectedName = state.nameEntryState.textFieldState.text.trim().toString()
                        )
                    )
                }

                is Event.UpdateCreatingState -> { state ->
                    state.copy(
                        creatingPool = state.creatingPool.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                Event.OnCreatePool,
                is Event.OnPoolCreated,
                Event.OnAmountConfirmed,
                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed,
                Event.OnDecimalPressed -> { state -> state }
            }
        }
    }
}