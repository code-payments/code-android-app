package com.flipcash.app.cash.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.cash.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
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
    tokenCoordinator: TokenCoordinator,
    transactionController: TransactionOperations,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<CashScreenViewModel.State, CashScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    private val numberInputHelper = NumberInputHelper()
    private val tokenInitialized = CompletableDeferred<Mint?>()

    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val token: TokenWithLocalizedBalance? = null,
        val currencyModel: CurrencyHolder = CurrencyHolder(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val limits: Limits? = null,
        val maxForGive: Pair<Double, CurrencyCode>? = null,
        val generatingBill: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val canGive: Boolean
            get() = (amountAnimatedModel.amountData.amount) > 0.00

        val maxAvailableForGive: String
            get() = maxForGive?.let { Fiat(it.first, it.second).formatted() }.orEmpty()


        val isError: Boolean
            get() {
                if (amountAnimatedModel.amountData.isEmpty()) return false
                if (maxForGive != null) {
                    val enteredAmount = Fiat(
                        fiat = amountAnimatedModel.amountData.amount,
                        currencyCode = maxForGive.second
                    )
                    val limit = Fiat(maxForGive.first, maxForGive.second)
                    if (enteredAmount.valueLessThanOrEqualTo(limit)) {
                        return false
                    }
                }

                return true
            }
    }

    sealed interface Event {
        data class InitializeToken(val mint: Mint?) : Event
        data class OnTokenSelected(val address: Mint) : Event
        data class OnTokenUpdated(val token: TokenWithLocalizedBalance) : Event
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


        data class AddCashToWallet(val amount: Fiat) : Event
        data class UpdateLoadingState(val loading: Boolean = false, val success: Boolean = false) :
            Event

        data class OpenScreen(val screen: AppRoute) : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        // this balance check differs from withdrawal due to the fact this is a localized check
        // whereas withdrawal is USD locked
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount
        val enteredAmount = Fiat(
            fiat = amount,
            currencyCode = stateFlow.value.currencyModel.code ?: CurrencyCode.USD
        )
        val tokenBalance = stateFlow.value.token?.balance?.nativeAmount ?: Fiat.Zero

        val isOverBalance = enteredAmount.valueGreaterThan(tokenBalance)
        if (isOverBalance) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_youNeedMoreCash),
                resources.getString(R.string.error_description_youNeedMoreCash),
                showCancel = false,
                actions = listOf(
                    BottomBarAction(
                        text = resources.getString(R.string.action_addMoreCash),
                        style = BottomBarManager.BottomBarButtonStyle.Filled,
                    ) {
                        viewModelScope.launch {
                            val rate = exchange.entryRate
                            val (token, balance) = stateFlow.value.token!!
                            val amountFiat = LocalFiat.valueExchangeIn(
                                amount =  Fiat(amount, rate.currency),
                                token = token,
                                balance = balance.underlyingTokenAmount,
                                rate = rate,
                            )

                            val neededAmount = amountFiat.nativeAmount - tokenBalance

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
        val amount = stateFlow.value.amountAnimatedModel.amountData.amount
        val currency = stateFlow.value.currencyModel
        val sendLimit =
            currency.code?.let { stateFlow.value.limits?.sendLimitFor(it) } ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached)
            )
        }
        isOverLimit
    }

    init {
        numberInputHelper.reset()

        eventFlow
            .filterIsInstance<Event.InitializeToken>()
            .take(1)
            .onEach { event ->
                if (event.mint != null) {
                    dispatchEvent(Event.OnTokenSelected(event.mint))
                }
                tokenInitialized.complete(event.mint)
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            val navMint = tokenInitialized.await()
            tokenCoordinator.observeSelectedTokenMint()
                .distinctUntilChanged()
                .let { if (navMint != null) it.drop(1) else it }
                .onEach { dispatchEvent(Event.OnTokenSelected(it)) }
                .launchIn(viewModelScope)
        }

        stateFlow
            .mapNotNull { it.selectedTokenAddress }
            .flatMapLatest { tokenAddress ->
                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    exchange.observeEntryRate(),
                ) { tokens, balance, rate ->
                    val token = tokens.find { it.address == tokenAddress } ?: return@combine null
                    TokenWithLocalizedBalance(
                        token = token,
                        balance = LocalFiat(
                            usdf = balance,
                            nativeAmount = balance.convertingTo(rate),
                        )
                    )
                }
            }.filterNotNull()
            .onEach {
                dispatchEvent(Event.OnTokenUpdated(it))
            }.mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.rate.currency.name)
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
            .map { it.limits to (it.token?.balance ?: LocalFiat.Zero) }
            .onEach { (limits, balance) ->
                val sendLimit = limits?.sendLimitFor(balance.rate.currency) ?: SendLimit.Zero
                val nextTransactionLimit = sendLimit.nextTransaction
                val max = min(nextTransactionLimit, balance.nativeAmount.decimalValue)
                dispatchEvent(Event.OnMaxDetermined(max, balance.rate.currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnGive>()
            .map { stateFlow.value.amountAnimatedModel }
            .filter { !(checkBalanceLimit() || checkSendLimit()) }
            .onEach { data ->
                dispatchEvent(Event.UpdateLoadingState(loading = true))
                val (token, balance) = stateFlow.value.token!!
                val rate = exchange.entryRate

                val amountFiat = LocalFiat.valueExchangeIn(
                    amount = Fiat(data.amountData.amount, rate.currency),
                    token = token,
                    balance = balance.underlyingTokenAmount,
                    rate = rate,
                )

                val bill = Bill.Cash(
                    token = stateFlow.value.token!!.token,
                    amount = amountFiat
                )

                dispatchEvent(Event.UpdateLoadingState(loading = false, success = true))
                dispatchEvent(Event.PresentBill(bill))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.AddCashToWallet>()
            .map { it.amount }
            .onEach {
                // route to buy the token
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.Token.Info(
                            mint = stateFlow.value.selectedTokenAddress!!,
                            isFundingShortfall = true
                        ),
                    )
                )
            }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        exchange.resetEntryToBalance()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnTokenSelected -> { state ->
                    state.copy(selectedTokenAddress = event.address)
                }

                is Event.OnTokenUpdated -> { state ->
                    state.copy(token = event.token)
                }

                is Event.OnAmountChanged -> { state ->
                    state.copy(
                        amountAnimatedModel = event.amountAnimatedModel
                    )
                }

                is Event.InitializeToken -> { state -> state }

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