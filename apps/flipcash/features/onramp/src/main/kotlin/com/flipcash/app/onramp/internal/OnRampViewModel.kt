package com.flipcash.app.onramp.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.OnRampController
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.onramp.internal.data.OnRampProviderDestination
import com.flipcash.app.onramp.internal.data.OnRampProviderItem
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
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
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

internal data class AmountEntryState(
    val limits: Limits? = null,
    val maxToAdd: Pair<Double, CurrencyCode>? = null,
    val currencyModel: CurrencyHolder = CurrencyHolder(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val confirmingAmount: LoadingSuccessState = LoadingSuccessState(),
    val selectedAmount: LocalFiat = LocalFiat.Zero,
) {
    val canAdd: Boolean
        get() = (amountAnimatedModel.amountData.amount) > 0.00

    val maxAvailableToAdd: String
        get() = maxToAdd?.let { Fiat(it.first, it.second).formatted() }.orEmpty()

    val isError: Boolean
        get() {
            if (amountAnimatedModel.amountData.isEmpty()) return false

            if (maxToAdd != null) {
                if ((amountAnimatedModel.amountData.amount) <= maxToAdd.first
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
        destination = OnRampProviderDestination.Screen(AppRoute.Sheets.TokenSelection(purpose = TokenPurpose.Deposit))
    )
)

@HiltViewModel
internal class OnRampViewModel @Inject constructor(
    userManager: UserManager,
    exchange: Exchange,
    transactionController: TransactionOperations,
    resources: ResourceHelper,
    onRampController: OnRampController,
    amountController: OnRampAmountController,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<OnRampViewModel.State, OnRampViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
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

        data class OnProviderSelected(val item: OnRampProvider) : Event

        data class OnVerificationNeeded(val phone: Boolean = false, val email: Boolean = false): Event

        data class OnPaymentLinkGenerated(val url: String) : Event
        data class OnBuyUrlGenerated(val url: String) : Event

        data object OnPaymentSuccess : Event
        data class OnPaymentError(val error: CoinbaseOnRampWebError) : Event
        data object OnPaymentCancel: Event

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

        data class OnAmountAccepted(val amount: LocalFiat) : Event

        data class CreateAndSendTransactionToWallet(val amount: LocalFiat) : Event
        // endregion
    }

    val checkFundingAmount: () -> Boolean = {
        val amount = stateFlow.value.amountEntryState.amountAnimatedModel.amountData.amount
        val currency = stateFlow.value.amountEntryState.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.amountEntryState.limits?.sendLimitFor(it) }
                ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showError(
                resources.getString(R.string.error_title_insufficientFunds),
                resources.getString(R.string.error_description_insufficientFunds)
            )
        }
        isOverLimit
    }

    init {
        numberInputHelper.reset()

        userManager.state
            .map { it.userProfile }
            .onEach {
                dispatchEvent(Event.OnPhoneVerificationChanged(it?.verifiedPhoneNumber != null))
                dispatchEvent(Event.OnEmailVerificationChanged(it?.verifiedEmailAddress != null))
            }.launchIn(viewModelScope)

        amountController.state
            .mapNotNull { it.provider }
            .onEach { dispatchEvent(Event.OnProviderSelected(it)) }
            .launchIn(viewModelScope)

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

                val localizedAmount = Fiat(data.amountData.amount, rate.currency)

                val amountFiat = LocalFiat(
                    usdf = localizedAmount.convertingTo(exchange.rateToUsd(rate.currency)!!),
                    nativeAmount = localizedAmount,
                )

                dispatchEvent(Event.OnAmountAccepted(amountFiat))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPaymentCancel>()
            .onEach { dispatchEvent(Event.UpdateConfirmingAmountState()) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPaymentError>()
            .map { it.error }
            .onEach { dispatchEvent(Event.UpdateConfirmingAmountState()) }
            .onEach {
                BottomBarManager.showError(
                    title = "Something Went Wrong",
                    message = "Please try again in a few minutes"
                )
//                when (it) {
//                    CoinbaseOnRampWebError.UNKNOWN -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_MISSING_TRANSACTION_UUID -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_CARD_NOT_DEBIT -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_GOOGLE_PAY_ERROR -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_BUY_FAILED -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_SEND_FAILED -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_AVS_VALIDATION_FAILED -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GUEST_TRANSACTION_TRANSACTION_FAILED -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_INTERNAL -> TODO()
//                    CoinbaseOnRampWebError.ERROR_CODE_GOOGLE_PAY_BUTTON_NOT_FOUND -> TODO()
//                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPaymentSuccess>()
            .onEach { }
            .launchIn(viewModelScope)

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
                                            AppRoute.Sheets.TokenSelection(purpose = TokenPurpose.Deposit)
                                        )

                                    is OnRampProvider.Coinbase -> {
                                        val hasVerifiedPhone = stateFlow.value.hasVerifiedPhone
                                        val hasVerifiedEmail = stateFlow.value.hasVerifiedEmail

                                        val mint = when (OnRampFlowTracker.source) {
                                            is AppRoute.Token.Info -> (OnRampFlowTracker.source as AppRoute.Token.Info).mint
                                            else -> null
                                        }

                                        val destination = if (!(hasVerifiedPhone && hasVerifiedEmail)) {
                                            AppRoute.Verification(
                                                origin = AppRoute.OnRamp.ProviderList(
                                                    from = OnRampFlowTracker.source!!
                                                ),
                                                target = AppRoute.OnRamp.AmountEntry(mint),
                                                includePhone = !hasVerifiedPhone,
                                                includeEmail = !hasVerifiedEmail,
                                            )
                                        } else {
                                            AppRoute.OnRamp.AmountEntry(mint)
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
            .map { it.item }
            // we are locking deeplink transfers to USD
            .filterIsInstance<OnRampProvider.UsesDeeplinks>()
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
                            OnRampType.Virtual -> {
                                onRampController.placeOrderExclusiveOfFees(selectedAmount.underlyingTokenAmount)
                                    .onSuccess {
                                        dispatchEvent(Event.OnPaymentLinkGenerated(it.url))
                                    }.onFailure { error ->
                                        dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = false))
                                        when (error) {
                                            is OnRampAuthError.CoinbasePhoneVerificationRequired -> {
                                                dispatchEvent(Event.OnVerificationNeeded(phone = true))
                                            }

                                            is OnRampAuthError.VerificationRequired -> {
                                                dispatchEvent(Event.OnVerificationNeeded(phone = error.phone, email = error.email))
                                            }

                                            else -> {
                                                BottomBarManager.showError(
                                                    title = "Error",
                                                    message = error.message ?: "Unknown error",
                                                )
                                            }
                                        }
                                    }
                            }
                            else -> Unit
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
                is Event.OnProviderSelected -> { state -> state.copy(selectedProvider = event.item as? OnRampProvider.ThirdParty) }
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

                is Event.OnPaymentCancel,
                is Event.OnVerificationNeeded,
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