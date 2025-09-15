package com.flipcash.app.onramp.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.onramp.CoinbaseOnRampWebError
import com.flipcash.app.onramp.OnRampController
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.onramp.internal.data.OnRampProviderDestination
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
import com.getcode.opencode.model.financial.LocalFiat
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

private val DefaultOnRampOptions = listOf(
    OnRampProviderItem(
        provider = OnRampProvider.ManualDeposit,
        destination = OnRampProviderDestination.Screen(AppRoute.Transfers.Deposit)
    )
)

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
        val providers: List<OnRampProviderItem> = DefaultOnRampOptions,
        val hasVerifiedPhone: Boolean = false,
        val hasVerifiedEmail: Boolean = false,
        val selectedProvider: OnRampProvider.ThirdParty? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
    )

    sealed interface Event {
        data class OnProvidersUpdated(val providers: List<OnRampProviderItem>) : Event

        data class OnPhoneVerificationChanged(val verified: Boolean) : Event
        data class OnEmailVerificationChanged(val verified: Boolean) : Event

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

        data class CreateAndSendTransactionToWallet(val amount: Fiat) : Event
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

        userManager.state
            .map { it.userProfile }
            .onEach {
                dispatchEvent(Event.OnPhoneVerificationChanged(it?.verifiedPhoneNumber != null))
                dispatchEvent(Event.OnEmailVerificationChanged(it?.verifiedEmailAddress != null))
            }.launchIn(viewModelScope)

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
                val rate = exchange.rateFor(stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD)
                    ?: exchange.entryRate

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

                dispatchEvent(Event.OnAmountAccepted(amountFiat.usdc))
            }.launchIn(viewModelScope)

        userManager.state
            .map { it.flags?.supportedOnRampProviders.orEmpty() }
            .onEach { providers ->
                val providersWithDeposit = providers
                    // always ensure that deposit is available
                    .ifEmpty { listOf(OnRampProvider.ManualDeposit) }
                    // ensure deposit is last
                    .sortedBy { if (it is OnRampProvider.ManualDeposit) 1 else 0 }

                val filteredProviders =
                    providersWithDeposit.filterIsInstance<OnRampProvider.Defined>()
                        .map { provider ->
                            OnRampProviderItem(
                                provider = provider,
                                destination = when (provider) {
                                    OnRampProvider.ManualDeposit ->
                                        OnRampProviderDestination.Screen(
                                            AppRoute.Transfers.Deposit
                                        )

                                    is OnRampProvider.Coinbase -> {
                                        val hasVerifiedPhone = stateFlow.value.hasVerifiedPhone
                                        val hasVerifiedEmail = stateFlow.value.hasVerifiedEmail

                                        val destination = if (!(hasVerifiedPhone && hasVerifiedEmail)) {
                                            AppRoute.Verification(
                                                origin = AppRoute.OnRamp.ProviderList(
                                                    from = OnRampFlowTracker.source!!
                                                ),
                                                target = AppRoute.OnRamp.AmountEntry,
                                                includePhone = !hasVerifiedPhone,
                                                includeEmail = !hasVerifiedEmail,
                                            )
                                        } else {
                                            AppRoute.OnRamp.AmountEntry
                                        }

                                        when (provider.type) {
                                            OnRampType.Virtual -> OnRampProviderDestination.Screen(destination)
                                            OnRampType.PhysicalDebit -> OnRampProviderDestination.Screen(destination)
                                            OnRampType.PhysicalCredit -> OnRampProviderDestination.Screen(destination)
                                        }
                                    }

                                    is OnRampProvider.UsesDeeplinks -> {
                                        OnRampProviderDestination.ExternalWalletConnection(provider)
                                    }
                                }
                            )
                        }
                dispatchEvent(Event.OnProvidersUpdated(filteredProviders))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnProviderSelected>()
            .map { it.item.provider }
            // we are locking Phantom transfers to USD
            .filterIsInstance<OnRampProvider.Phantom>()
            .mapNotNull { exchange.getCurrency(CurrencyCode.USD.name) }
            .onEach { dispatchEvent(Event.OnCurrencyChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountAccepted>()
            .mapNotNull {
                val provider = stateFlow.value.selectedProvider ?: return@mapNotNull null
                it.amount to provider
            }
            .onEach { (selectedAmount, provider) ->
                when (provider) {
                    is OnRampProvider.Coinbase -> {
                        when (provider.type) {
                            OnRampType.Virtual -> onRampController.placeOrderExclusiveOfFees(
                                selectedAmount
                            ).onSuccess {
                                dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = true))
                                dispatchEvent(Event.OnPaymentLinkGenerated(it.url))
                            }.onFailure { error ->
                                dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = false))
                                BottomBarManager.showError(
                                    title = "Error",
                                    message = error.message ?: "Unknown error",
                                )
                            }

                            OnRampType.PhysicalDebit,
                            OnRampType.PhysicalCredit -> {
                                onRampController.generateLegacyOnRampUrl(selectedAmount)
                                    .onSuccess {
                                        dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = true))
                                        dispatchEvent(Event.OnBuyUrlGenerated(it))
                                    }.onFailure {
                                        dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = false))
                                        BottomBarManager.showError(
                                            title = "Error",
                                            message = it.message ?: "Unknown error",
                                        )
                                    }
                            }
                        }
                    }

                    is OnRampProvider.UsesDeeplinks -> {
                        dispatchEvent(Event.CreateAndSendTransactionToWallet(selectedAmount))
                    }
                }
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProviderSelected -> { state -> state.copy(selectedProvider = event.item.provider as? OnRampProvider.ThirdParty) }
                is Event.OnProvidersUpdated -> { state -> state.copy(providers = event.providers) }

                is Event.OnPhoneVerificationChanged -> { state -> state.copy(hasVerifiedPhone = event.verified) }
                is Event.OnEmailVerificationChanged -> { state -> state.copy(hasVerifiedEmail = event.verified) }

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

                is Event.CreateAndSendTransactionToWallet,
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