package com.flipcash.app.onramp.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.mapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.onramp.CoinbaseOnRampState
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.OnRampPaymentError
import com.flipcash.features.onramp.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
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
    val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
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

@HiltViewModel
internal class OnRampViewModel @Inject constructor(
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val resources: ResourceHelper,
    private val onRampController: CoinbaseOnRampController,
    tokenController: TokenController,
    transactionController: TransactionOperations,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<OnRampViewModel.State, OnRampViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    private val numberInputHelper = NumberInputHelper()

    data class State(
        val loading: Boolean = false,
        val mint: Mint? = null,
        val token: Token? = null,
        val canChangeCurrency: Boolean = false,
        val hasVerifiedPhone: Boolean = false,
        val hasVerifiedEmail: Boolean = false,
        val selectedProvider: OnRampProvider.ThirdParty? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
    ) {
        val minimumPurchaseAmount = 5.toFiat()
    }

    sealed interface Event {
        data class OnMintChanged(val mint: Mint) : Event
        data class OnTokenChanged(val token: Token) : Event

        data class OnPhoneVerificationChanged(val verified: Boolean) : Event
        data class OnEmailVerificationChanged(val verified: Boolean) : Event

        data class OnProviderSelected(val item: OnRampProvider) : Event

        data class OnVerificationNeeded(val phone: Boolean = false, val email: Boolean = false) :
            Event

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

        data class OnAmountAccepted(val amount: VerifiedFiat) : Event

        data class CreateAndSendTransactionToWallet(val amount: VerifiedFiat) : Event
        // endregion
    }

    val checkFundingAmount: () -> Boolean = {
        val amount = stateFlow.value.amountEntryState.amountAnimatedModel.amountData.amount
        val currency = stateFlow.value.amountEntryState.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.amountEntryState.limits?.sendLimitFor(it) }
                ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.maxPerDay
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_insufficientFunds),
                resources.getString(R.string.error_description_insufficientFunds)
            )
        }
        isOverLimit
    }

    init {
        numberInputHelper.reset()

        onRampController.state
            .filter { it !is CoinbaseOnRampState.Paying }
            .onEach {
                if (stateFlow.value.amountEntryState.confirmingAmount.loading) {
                    dispatchEvent(Event.UpdateConfirmingAmountState())
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMintChanged>()
            .map { it.mint }
            .map { tokenController.getTokenMetadata(it) }
            .mapResult { it.token }
            .onResult(
                onSuccess = {
                    dispatchEvent(Event.OnTokenChanged(it))
                }
            ).launchIn(viewModelScope)

        dispatchEvent(Event.OnProviderSelected(OnRampProvider.Coinbase(OnRampType.Virtual)))

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
                val nextTransactionLimit = sendLimit.maxPerDay
                dispatchEvent(Event.OnMaxDetermined(nextTransactionLimit, currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { stateFlow.value.amountEntryState.amountAnimatedModel }
            .filter { !(checkFundingAmount()) }
            .onEach { data ->
                dispatchEvent(Event.UpdateConfirmingAmountState(loading = true))
                val rate = exchange.rateFor(
                    stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD
                ) ?: exchange.entryRate

                val localizedAmount = Fiat(data.amountData.amount, rate.currency)

                if (stateFlow.value.selectedProvider is OnRampProvider.Coinbase) {
                    if (localizedAmount < stateFlow.value.minimumPurchaseAmount) {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_onrampAmountTooLow),
                            message = resources.getString(R.string.error_description_onrampAmountTooLow)
                        )
                        dispatchEvent(Event.UpdateConfirmingAmountState())
                        return@onEach
                    }
                }

                val amountFiat = verifiedFiatCalculator.compute(
                    amount = localizedAmount,
                    token = Token.usdf,
                    rate = rate,
                ).getOrElse { cause ->
                    dispatchEvent(Event.UpdateConfirmingAmountState())
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_staleRates),
                        message = resources.getString(R.string.error_description_staleRates),
                    )
                    return@onEach
                }

                dispatchEvent(Event.OnAmountAccepted(amountFiat))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnProviderSelected>()
            .map { it.item }
            // we are locking deeplink transfers and onramp buys to USD
            .filter { it is OnRampProvider.UsesDeeplinks || it is OnRampProvider.Coinbase }
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
                                val token = stateFlow.value.token
                                if (token == null) {
                                    dispatchEvent(Event.UpdateConfirmingAmountState())
                                    return@onEach
                                }

                                onRampController.placeOrderAndStartPayment(
                                    amount = selectedAmount.localFiat.underlyingTokenAmount,
                                    token = token,
                                    verifiedFiat = selectedAmount,
                                ).onFailure { error ->
                                    dispatchEvent(Event.UpdateConfirmingAmountState())
                                    when (error) {
                                        is OnRampAuthError.CoinbasePhoneVerificationRequired -> {
                                            dispatchEvent(Event.OnVerificationNeeded(phone = true))
                                        }

                                        is OnRampAuthError.VerificationRequired -> {
                                            dispatchEvent(
                                                Event.OnVerificationNeeded(
                                                    phone = error.phone,
                                                    email = error.email
                                                )
                                            )
                                        }

                                        is OnRampPaymentError.GooglePayNotSupported -> {
                                            BottomBarManager.showAlert(
                                                title = resources.getString(R.string.error_title_onrampGooglePayNotSupported),
                                                message = resources.getString(R.string.error_description_onrampGooglePayNotSupported),
                                            )
                                        }

                                        is OnRampPaymentError.GooglePayNoPaymentMethod -> {
                                            BottomBarManager.showAlert(
                                                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                                                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
                                            )
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

    override fun onCleared() {
        exchange.resetEntryToBalance()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMintChanged -> { state -> state.copy(mint = event.mint) }
                is Event.OnTokenChanged -> { state -> state.copy(token = event.token) }
                is Event.OnProviderSelected -> { state ->
                    state.copy(
                        canChangeCurrency = event.item !is OnRampProvider.Phantom && event.item !is OnRampProvider.Coinbase,
                        selectedProvider = event.item as? OnRampProvider.ThirdParty
                    )
                }

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

                is Event.OnVerificationNeeded,
                is Event.CreateAndSendTransactionToWallet,
                Event.OnAmountConfirmed,
                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed,
                Event.OnDecimalPressed -> { state -> state }
            }
        }
    }
}