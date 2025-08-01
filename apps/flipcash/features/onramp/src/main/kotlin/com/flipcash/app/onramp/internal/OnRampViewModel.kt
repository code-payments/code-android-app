package com.flipcash.app.onramp.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.onramp.CoinbaseOnRampWebError
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.OnRampController
import com.flipcash.app.onramp.internal.data.OnRampProviderItem
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AmountEntryState(
    val limits: Limits? = null,
    val maxToAdd: Pair<Double, CurrencyCode>? = null,
    val currencyModel: CurrencyHolder = CurrencyHolder(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val confirmingAmount: LoadingSuccessState = LoadingSuccessState(),
    val selectedAmount: Fiat = Fiat.Zero,
) {
    val canAdd: Boolean
        get() = (amountAnimatedModel.amountData.amount.toDoubleOrNull()
            ?: 0.0) > 0.00

    val maxAvailableToAdd: String
        get() = maxToAdd?.let { Fiat(it.first, it.second).formatted() }.orEmpty()

    val isError: Boolean
        get() {
            if (amountAnimatedModel.amountData.amount.isEmpty()) return false

            if (maxToAdd != null) {
                if ((amountAnimatedModel.amountData.amount.toDoubleOrNull()
                        ?: 0.0) <= maxToAdd.first
                ) {
                    return false
                }
            }

            return true
        }
}

@HiltViewModel
internal class OnRampViewModel @Inject constructor(
    userManager: UserManager,
    exchange: Exchange,
    transactionController: TransactionController,
    resources: ResourceHelper,
    onRampController: OnRampController,
) : BaseViewModel2<OnRampViewModel.State, OnRampViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    private val numberInputHelper = NumberInputHelper()

    data class State(
        val loading: Boolean = false,
        val providers: List<OnRampProviderItem> = emptyList(),
        val selectedProvider: OnRampProvider.Defined? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
    )

    sealed interface Event {
        data class OnProvidersUpdated(val providers: List<OnRampProviderItem>) : Event
        data class OnProviderSelected(val item: OnRampProviderItem) : Event

        data class OnPaymentLinkGenerated(val url: String) : Event
        data class OnBuyUrlGenerated(val url: String) : Event

        data object OnPaymentSuccess : Event
        data class OnPaymentError(val error: CoinbaseOnRampWebError) : Event

        // region amount entry events
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) : Event
        data class OnLimitsChanged(val limits: Limits?) : Event

        data class OnNumberPressed(val number: Int) : Event
        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnAmountChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) : Event

        data class OnCurrencyChanged(val currency: Currency) : Event

        data object OnAmountConfirmed : Event
        data class UpdateConfirmingAmountState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data class OnAmountAccepted(val amount: Fiat) : Event
        // endregion
    }

    val checkFundingAmount: () -> Boolean = {
        val amount =
            stateFlow.value.amountEntryState.amountAnimatedModel.amountData.amount.toDoubleOrNull()
                ?: 0.0
        val currency = stateFlow.value.amountEntryState.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.amountEntryState.limits?.sendLimitFor(it) }
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

        exchange.observeEntryRate()
            .mapNotNull {
                exchange.getCurrency(it.currency.name)
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
                numberInputHelper.fractionUnits =
                    stateFlow.value.amountEntryState.currencyModel.fractionUnits
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
                val current = stateFlow.value.amountEntryState.amountAnimatedModel
                val model = stateFlow.value.amountEntryState.amountAnimatedModel
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnAmountChanged(updated))
            }.launchIn(viewModelScope)

        stateFlow
            .map { it.amountEntryState }
            .filter { it.limits != null }
            .map { it.limits to it.currencyModel.code }
            .mapNotNull {
                val currency = it.second ?: return@mapNotNull null
                it.first to currency
            }
            .onEach { (limits, currency) ->
                val sendLimit = limits?.sendLimitFor(currency) ?: SendLimit.Zero
                val nextTransactionLimit = sendLimit.nextTransaction
                dispatchEvent(Event.OnMaxDetermined(nextTransactionLimit, currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { stateFlow.value.amountEntryState.amountAnimatedModel }
            .filter { !(checkFundingAmount()) }
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

        userManager.state
            .map { it.flags?.supportedOnRampProviders.orEmpty() }
            .onEach { providers ->
                val providersWithDeposit = providers.plus(OnRampProvider.CryptoDeposit)
                val filteredProviders =
                    providersWithDeposit.filterIsInstance<OnRampProvider.Defined>()
                        .map { provider ->
                            OnRampProviderItem(
                                provider = provider,
                                destination = when (provider) {
                                    OnRampProvider.CryptoDeposit -> NavScreenProvider.HomeScreen.Menu.Transfers.Learn(
                                        TransferDirection.Incoming
                                    )

                                    is OnRampProvider.Coinbase -> when (provider.type) {
                                        OnRampType.Virtual -> TODO()
                                        OnRampType.PhysicalDebit -> NavScreenProvider.HomeScreen.OnRamp.Amount
                                        OnRampType.PhysicalCredit -> NavScreenProvider.HomeScreen.OnRamp.Amount
                                    }
                                }
                            )
                        }
                dispatchEvent(Event.OnProvidersUpdated(filteredProviders))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountAccepted>()
            .map { it.amount }
            .onEach { selectedAmount ->
                val provider =
                    stateFlow.value.selectedProvider as? OnRampProvider.Coinbase ?: return@onEach
                when (provider.type) {
                    OnRampType.Virtual -> onRampController.placeOrderExclusiveOfFees(selectedAmount)
                        .onSuccess {
                            dispatchEvent(Event.OnPaymentLinkGenerated(it.url))
                        }.onFailure { error ->
                            when (error) {
                                is OnRampAuthError.EmailVerificationRequired -> {
                                    BottomBarManager.showError(
                                        title = "Email verification required",
                                        message = "Please verify your email address to continue",
                                    )
                                }

                                is OnRampAuthError.PhoneVerificationRequired -> {
                                    BottomBarManager.showError(
                                        title = "Phone verification required",
                                        message = "Please verify your phone number to continue",
                                    )
                                }

                                is OnRampAuthError.CoinbasePhoneVerificationRequired -> {
                                    BottomBarManager.showError(
                                        title = "Phone verification required",
                                        message = "Please verify your phone number with Coinbase to continue",
                                    ) {
//                                        dispatchEvent(Event.OnPhoneVerificationRequired(error.url))
                                    }
                                }

                                else -> {
                                    BottomBarManager.showError(
                                        title = "Error",
                                        message = error.message ?: "Unknown error",
                                    )
                                }
                            }
                        }

                    OnRampType.PhysicalDebit,
                    OnRampType.PhysicalCredit -> {
                        TODO()
                    }
                }
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProviderSelected -> { state -> state.copy(selectedProvider = event.item.provider) }
                is Event.OnProvidersUpdated -> { state -> state.copy(providers = event.providers) }

                is Event.OnAmountAccepted -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            selectedAmount = event.amount
                        )
                    )
                }

                is Event.OnAmountChanged -> { state ->
                    state.copy(amountEntryState = state.amountEntryState.copy(amountAnimatedModel = event.amountAnimatedModel))
                }

                is Event.OnCurrencyChanged -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            currencyModel = CurrencyHolder(
                                event.currency
                            )
                        )
                    )
                }

                is Event.OnLimitsChanged -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            limits = event.limits

                        )
                    )
                }

                is Event.OnMaxDetermined -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            maxToAdd = event.max to event.currencyCode
                        ),
                    )
                }

                is Event.UpdateConfirmingAmountState -> { state ->
                    val entryState = state.amountEntryState
                    val loadingSuccess = entryState.confirmingAmount
                    state.copy(
                        amountEntryState = entryState.copy(
                            confirmingAmount = loadingSuccess.copy(
                                loading = event.loading,
                                success = event.success
                            )
                        )
                    )
                }

                is Event.OnPaymentSuccess,
                is Event.OnPaymentError,
                is Event.OnPaymentLinkGenerated,
                is Event.OnBuyUrlGenerated,
                Event.OnAmountConfirmed,
                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed,
                Event.OnDecimalPressed -> { state -> state }
            }
        }
    }
}