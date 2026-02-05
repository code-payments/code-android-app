package com.flipcash.app.tokens

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.to
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.times
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AmountEntryState(
    val limits: Limits? = null,
    val currencyModel: CurrencyHolder = CurrencyHolder(),
    val maxToAdd: Pair<Double, CurrencyCode>? = null,
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val selectedAmount: LocalFiat = LocalFiat.Zero,
)

@HiltViewModel
class BuySellSwapTokenViewModel @Inject constructor(
    userManager: UserManager,
    exchange: Exchange,
    transactionController: TransactionController,
    resources: ResourceHelper,
    tokenController: TokenController,
    feedCoordinator: ActivityFeedCoordinator,
) : BaseViewModel2<BuySellSwapTokenViewModel.State, BuySellSwapTokenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val loading: Boolean = false,
        val purpose: TokenSwapPurpose? = null,
        val tokenWithBalance: TokenWithBalance? = null,
        val reservesWithBalance: TokenWithBalance? = null,
        val swapId: SwapId? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
        val buyProgress: LoadingSuccessState = LoadingSuccessState(),
        val sellProgress: LoadingSuccessState = LoadingSuccessState(),
        val processingProgress: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val sellFee: Double?
            get() {
                val feeBps = tokenWithBalance?.token?.launchpadMetadata?.sellFeeBps ?: return null
                val fee = feeBps / 100.0 // basis points to whole percent
                return fee
            }

        val tokenName: String
            get() = tokenWithBalance?.displayName.orEmpty()

        val canTransact: Boolean
            get() = (amountEntryState.amountAnimatedModel.amountData.amount) > 0.00 && buyProgress.isIdle && sellProgress.isIdle && processingProgress.isIdle

        val maxAvailableToSwap: String
            get() = when (purpose) {
                is TokenSwapPurpose.Buy -> reservesBalance.formatted()
                is TokenSwapPurpose.FundWithWallet -> amountEntryState.maxToAdd?.let {
                    Fiat(
                        it.first,
                        it.second
                    ).formatted()
                }.orEmpty()

                is TokenSwapPurpose.Sell -> tokenBalance.formatted()
                null -> ""
            }

        val tokenBalance: Fiat
            get() = tokenWithBalance?.balance ?: Fiat.Zero

        val reservesBalance: Fiat
            get() = reservesWithBalance?.balance ?: Fiat.Zero

        val enteredAmount
            get() = Fiat(
                fiat = amountEntryState.amountAnimatedModel.amountData.amount,
                currencyCode = tokenBalance.currencyCode
            )

        val feeAmount: Fiat
            get() {
                val fee = sellFee ?: return Fiat.Zero
                return enteredAmount * (fee / 100.0)
            }

        val netTransferAmount: Fiat
            get() = when (purpose) {
               is TokenSwapPurpose.BalanceIncrease -> enteredAmount
               else -> enteredAmount - feeAmount
            }

        val transactionLimit: Fiat
            get() {
                return when (purpose) {
                    is TokenSwapPurpose.Buy -> reservesBalance
                    is TokenSwapPurpose.FundWithWallet -> {
                        val sendLimit =
                            enteredAmount.currencyCode.let {
                                amountEntryState.limits?.sendLimitFor(
                                    it
                                )
                            }
                                ?: SendLimit.Zero

                        sendLimit.nextTransaction.toFiat(enteredAmount.currencyCode)
                    }

                    is TokenSwapPurpose.Sell -> tokenBalance
                    null -> Fiat.Zero
                }
            }

        val isError: Boolean
            get() {
                if (amountEntryState.amountAnimatedModel.amountData.isEmpty()) return false
                return !enteredAmount.valueLessThanOrEqualTo(transactionLimit)
            }
    }

    sealed interface Event {
        data class OnPurposeChanged(val purpose: TokenSwapPurpose) : Event
        data class OnSelectedTokenChanged(val token: TokenWithBalance) : Event
        data class OnReservesUpdated(val reserves: TokenWithBalance) : Event

        data class OnLimitsChanged(val limits: Limits?) : Event

        // region amount entry events
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) : Event

        data class OnNumberPressed(val number: Int) : Event

        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event

        data class OnAmountChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) : Event

        data class OnCurrencyChanged(val currency: Currency) : Event

        data object OnAmountConfirmed : Event

        data object OnSellConfirmed : Event

        data class UpdateBuyState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data class UpdateSellState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data class OnSwapIdChanged(val swapId: SwapId) : Event

        data class CreateAndSendTransactionToWallet(val token: Token, val amount: LocalFiat) : Event

        data class OnAmountAccepted(val amount: LocalFiat) : Event

        data class ProceedWithPurchase(val amount: LocalFiat) : Event
        data class ProceedWithSale(val amount: LocalFiat) : Event

        data object ShowSellReceipt : Event

        data class OnPurchaseSubmitted(val token: Token, val swapId: SwapId) : Event
        data class OnSellSubmitted(val token: Token, val swapId: SwapId) : Event

        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event
        data object OnTransactionSuccessful : Event

        data object Exit : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        val amount =
            stateFlow.value.amountEntryState.amountAnimatedModel.amountData.amount
        val conversionRate =
            exchange.rateToUsd(
                stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD
            ) ?: Rate.ignore
        val enteredInUsdf = Fiat(
            fiat = amount,
            currencyCode = stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD
        ).convertingTo(conversionRate)
        val tokenBalance = stateFlow.value.tokenBalance
        val reservesBalance = stateFlow.value.reservesBalance

        when (stateFlow.value.purpose) {
            is TokenSwapPurpose.BalanceIncrease -> {
                val isOverBalance = enteredInUsdf > reservesBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                }
                isOverBalance
            }

            is TokenSwapPurpose.BalanceDecrease -> {
                val isOverBalance = enteredInUsdf > tokenBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                }
                isOverBalance
            }

            null -> true
        }
    }

    val checkFundingAmount: () -> Boolean = {
        val limit = stateFlow.value.transactionLimit
        val isOverLimit = stateFlow.value.enteredAmount.valueGreaterThan(limit)
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

        eventFlow.filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                val mint = when (purpose) {
                    is TokenSwapPurpose.Buy -> purpose.mint
                    is TokenSwapPurpose.FundWithWallet -> purpose.mint
                    is TokenSwapPurpose.Sell -> purpose.mint
                }

                combine(
                    tokenController.tokenBalances,
                    when (purpose) {
                        is TokenSwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
                        else -> exchange.observeEntryRate()
                    },
                ) { tokens, rate ->
                    var token = tokens.find { it.token.address == mint }
                    if (token == null) {
                        val tokenRef = tokenController.getTokenMetadata(mint).getOrNull()
                        if (tokenRef != null) {
                            token = TokenWithBalance(
                                token = tokenRef.token,
                                balance = Fiat.Zero,
                            )
                        }
                    }

                    if (token == null) {
                        trace(tag = "BuySellSwap", message = "Unable to find token for mint ${mint.base58()}")
                        dispatchEvent(Event.Exit)
                        return@combine null
                    }

                    val balance = LocalFiat(
                        usdf = token.balance,
                        nativeAmount = token.balance.convertingTo(rate),
                    )

                    TokenWithBalance(token.token, balance.nativeAmount)
                }
            }
            .filterNotNull()
            .onEach { token -> dispatchEvent(Event.OnSelectedTokenChanged(token)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                val tokenAddress = when (purpose) {
                    is TokenSwapPurpose.Buy -> Mint.usdf
                    is TokenSwapPurpose.FundWithWallet -> Mint.usdf
                    is TokenSwapPurpose.Sell -> purpose.mint
                }

                combine(
                    tokenController.tokens,
                    tokenController.balanceForToken(tokenAddress),
                    when (purpose) {
                        is TokenSwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
                        else -> exchange.observeEntryRate()
                    },
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
            }.filterNotNull().mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.rate.currency.name)
            }.onEach {
                dispatchEvent(Event.OnCurrencyChanged(it))
            }.launchIn(viewModelScope)

        combine(
            tokenController.observeReservesBalance(),
            when (stateFlow.value.purpose) {
                is TokenSwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
                else -> exchange.observeEntryRate()
            },
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
            )
        }.onEach {
            dispatchEvent(Event.OnReservesUpdated(TokenWithBalance(Token.usdf, it.nativeAmount)))
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
            .filterNot {
                val purpose = stateFlow.value.purpose
                // Don't check balance if funds are coming from external
                if (purpose !is TokenSwapPurpose.FundWithWallet) {
                    checkBalanceLimit()
                } else {
                    false
                }
            }.filterNot { checkFundingAmount() }
            .mapNotNull {
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                it to purpose
            }
            .onEach { (data, purpose) ->
                when (purpose) {
                    is TokenSwapPurpose.Buy -> {
                        val rate = exchange.entryRate
                        // buy with reserves
                        val amountFiat = LocalFiat.valueExchangeIn(
                            amount = Fiat(data.amountData.amount, rate.currency),
                            token = Token.usdf,
                            balance = stateFlow.value.reservesBalance,
                            rate = rate
                        )

                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        dispatchEvent(Event.OnAmountAccepted(amountFiat))
                        dispatchEvent(Event.ProceedWithPurchase(amountFiat))
                    }

                    is TokenSwapPurpose.FundWithWallet -> {
                        val rate = exchange.rateForUsd()
                        // funding through external wallet
                        val nativeAmount = Fiat(data.amountData.amount, rate.currency)
                        val underlyingAmount = nativeAmount.convertingToUsdIfNeeded(rate)
                        val amountFiat = LocalFiat(
                            usdf = underlyingAmount,
                            nativeAmount = nativeAmount,
                        )

                        dispatchEvent(Event.OnAmountAccepted(amountFiat))
                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        dispatchEvent(
                            Event.CreateAndSendTransactionToWallet(
                                token = stateFlow.value.tokenWithBalance!!.token,
                                amount = amountFiat
                            )
                        )
                    }

                    is TokenSwapPurpose.Sell -> {
                        val rate = exchange.entryRate
                        val tokenWithBalance = stateFlow.value.tokenWithBalance!!
                        val amountFiat = LocalFiat.valueExchangeIn(
                            amount = Fiat(data.amountData.amount, rate.currency),
                            token = tokenWithBalance.token,
                            balance = tokenWithBalance.balance,
                            rate = rate,
                        )

                        dispatchEvent(Event.OnAmountAccepted(amountFiat))
                        dispatchEvent(Event.ShowSellReceipt)
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSellConfirmed>()
            .map { stateFlow.value.amountEntryState.selectedAmount }
            .onEach {
                dispatchEvent(Event.UpdateSellState(loading = true))
                dispatchEvent(Event.ProceedWithSale(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithPurchase>()
            .onEach { dispatchEvent(Event.UpdateBuyState(loading = true)) }
            .map { it.amount }
            .mapNotNull { amount ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                owner to purpose to amount
            }
            .map { (owner, purpose, amount) -> owner to stateFlow.value.tokenWithBalance!!.token to amount }
            .onEach { (owner, token, amount) ->
                transactionController.buy(
                    owner = owner,
                    amount = amount,
                    of = token,
                ).onSuccess { swapId ->
                    dispatchEvent(Event.OnPurchaseSubmitted(token, swapId))
                    // buy submitted from reserves, drop reserves balance
                    tokenController.subtract(Token.usdf, amount)
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = true))
                }.onFailure {
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithSale>()
            .onEach { dispatchEvent(Event.UpdateSellState(loading = true)) }
            .map { it.amount }
            .mapNotNull { amount ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                owner to purpose to amount
            }
            .map { (owner, purpose, amount) -> owner to stateFlow.value.tokenWithBalance!!.token to amount }
            .onEach { (owner, token, amount) ->
                transactionController.sell(
                    owner = owner,
                    amount = amount,
                    of = token,
                ).onSuccess { swapId ->
                    dispatchEvent(Event.OnSellSubmitted(token, swapId))
                    // sell submitted, drop from balance
                    tokenController.subtract(token, amount)
                    dispatchEvent(Event.UpdateSellState(loading = false, success = true))
                }.onFailure {
                    dispatchEvent(Event.UpdateSellState(loading = false, success = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }.launchIn(viewModelScope)

        stateFlow
            .mapNotNull { it.swapId }
            .distinctUntilChanged()
            .mapNotNull { swapId ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                owner to swapId
            }
            .map { (owner, swapId) ->
                dispatchEvent(Event.UpdateProcessingState(loading = true))
                transactionController.pollSwapForState(
                    swapId = swapId,
                    owner = owner,
                    targetState = SwapState.FINALIZED
                )
            }.onResult(
                onSuccess = {
                    val token = stateFlow.value.tokenWithBalance!!.token
                    viewModelScope.launch { tokenController.updateTokenAccount(token) }
                    viewModelScope.launch {
                        // update activity feed to grab the tx as a result of this buy/sell
                        feedCoordinator.fetchSinceLatest()
                    }
                    dispatchEvent(Event.OnTransactionSuccessful)
                    dispatchEvent(Event.UpdateProcessingState(loading = false, success = true))
                },
                onError = {
                    // TODO: show error
                    dispatchEvent(Event.UpdateProcessingState(loading = false, success = false, error = true))
                }
            ).launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPurposeChanged -> { state -> state.copy(purpose = event.purpose) }
                is Event.OnSelectedTokenChanged -> { state -> state.copy(tokenWithBalance = event.token) }
                is Event.OnReservesUpdated -> { state -> state.copy(reservesWithBalance = event.reserves) }

                is Event.OnAmountAccepted -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            selectedAmount = event.amount,
                        )
                    )
                }

                is Event.OnAmountChanged -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            amountAnimatedModel = event.amountAnimatedModel
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

                is Event.OnCurrencyChanged -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            currencyModel = CurrencyHolder(event.currency)
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

                Event.OnAmountConfirmed,
                Event.OnBackspace,
                Event.OnDecimalPressed,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed -> { state -> state }

                is Event.UpdateBuyState -> { state ->
                    val entryState = state.buyProgress
                    state.copy(
                        buyProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.UpdateSellState -> { state ->
                    val entryState = state.sellProgress
                    state.copy(
                        sellProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.UpdateProcessingState -> { state ->
                    val entryState = state.processingProgress
                    state.copy(
                        processingProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success,
                            error = event.error,
                        )
                    )
                }

                is Event.OnSwapIdChanged -> { state -> state.copy(swapId = event.swapId) }

                is Event.ProceedWithPurchase -> { state -> state }
                is Event.ProceedWithSale -> { state -> state }
                is Event.CreateAndSendTransactionToWallet -> { state -> state }
                Event.OnTransactionSuccessful -> { state -> state }
                is Event.OnPurchaseSubmitted -> { state -> state }
                is Event.OnSellConfirmed -> { state -> state }
                is Event.OnSellSubmitted -> { state -> state }
                Event.ShowSellReceipt -> { state -> state }
                Event.Exit -> { state -> state }
            }
        }
    }
}