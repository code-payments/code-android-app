package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.to
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.core.errors.SwapError
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
    val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
)

@HiltViewModel
class SwapViewModel @Inject constructor(
    userManager: UserManager,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    transactionController: TransactionOperations,
    resources: ResourceHelper,
    private val tokenCoordinator: TokenCoordinator,
    feedCoordinator: ActivityFeedCoordinator,
    private val analytics: FlipcashAnalyticsService,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<SwapViewModel.State, SwapViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val loading: Boolean = false,
        val purpose: SwapPurpose? = null,
        val tokenWithBalance: TokenWithBalance? = null,
        val reservesWithBalance: TokenWithBalance? = null,
        val swapId: SwapId? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
        val buyProgress: LoadingSuccessState = LoadingSuccessState(),
        val sellProgress: LoadingSuccessState = LoadingSuccessState(),
        val processingProgress: LoadingSuccessState = LoadingSuccessState(),
        val confirmedNetTransferAmount: Fiat? = null,
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
                is SwapPurpose.Buy -> reservesBalance.formatted()
                is SwapPurpose.FundWithWallet -> amountEntryState.maxToAdd?.let {
                    Fiat(
                        it.first,
                        it.second
                    ).formatted()
                }.orEmpty()

                is SwapPurpose.Sell -> tokenBalance.formatted()
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
            get() = confirmedNetTransferAmount ?: when (purpose) {
               is SwapPurpose.BalanceIncrease -> enteredAmount
               else -> Fiat(fiat = enteredAmount.decimalValue - feeAmount.decimalValue, currencyCode = enteredAmount.currencyCode)
            }

        val transactionLimit: Fiat
            get() {
                return when (purpose) {
                    is SwapPurpose.Buy -> reservesBalance
                    is SwapPurpose.FundWithWallet -> {
                        val sendLimit =
                            enteredAmount.currencyCode.let {
                                amountEntryState.limits?.sendLimitFor(
                                    it
                                )
                            } ?: SendLimit.Zero

                        sendLimit.maxPerDay.toFiat(enteredAmount.currencyCode)
                    }

                    is SwapPurpose.Sell -> tokenBalance
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
        data class OnPurposeChanged(val purpose: SwapPurpose) : Event
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

        data class CreateAndSendTransactionToWallet(val token: Token, val amount: VerifiedFiat) : Event

        data class OnAmountAccepted(val amount: VerifiedFiat, val netTransferAmount: Fiat) : Event

        data class ProceedWithPurchase(val amount: VerifiedFiat) : Event
        data class ProceedWithSale(val amount: VerifiedFiat) : Event

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
            is SwapPurpose.BalanceIncrease -> {
                val isOverBalance = enteredInUsdf > reservesBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                }
                isOverBalance
            }

            is SwapPurpose.BalanceDecrease -> {
                val isOverBalance = enteredInUsdf > tokenBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showAlert(
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
            BottomBarManager.showAlert(
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
                    is SwapPurpose.Buy -> purpose.mint
                    is SwapPurpose.FundWithWallet -> purpose.mint
                    is SwapPurpose.Sell -> purpose.mint
                }

                combine(
                    tokenCoordinator.tokenBalances,
                    when (purpose) {
                        is SwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
                        else -> exchange.observeEntryRate()
                    },
                ) { tokens, rate ->
                    var token = tokens.find { it.token.address == mint }
                    if (token == null) {
                        val tokenRef = tokenCoordinator.getTokenMetadata(mint).getOrNull()
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
                    is SwapPurpose.Buy -> Mint.usdf
                    is SwapPurpose.FundWithWallet -> Mint.usdf
                    is SwapPurpose.Sell -> purpose.mint
                }

                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    when (purpose) {
                        is SwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
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
            tokenCoordinator.observeReservesBalance(),
            when (stateFlow.value.purpose) {
                is SwapPurpose.FundWithWallet -> flowOf(exchange.rateForUsd())
                else -> exchange.observeEntryRate()
            },
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
            )
        }.filter {
            stateFlow.value.buyProgress.isIdle && stateFlow.value.sellProgress.isIdle
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
                val nextTransactionLimit = sendLimit.maxPerDay
                dispatchEvent(Event.OnMaxDetermined(nextTransactionLimit, currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { stateFlow.value.amountEntryState.amountAnimatedModel }
            .filterNot {
                val purpose = stateFlow.value.purpose
                // Don't check balance if funds are coming from external
                if (purpose !is SwapPurpose.FundWithWallet) {
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
                    is SwapPurpose.Buy -> {
                        val rate = exchange.entryRate
                        // buy with reserves
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = Fiat(data.amountData.amount, rate.currency),
                            token = Token.usdf,
                            balance = stateFlow.value.reservesBalance.convertingToUsdIfNeeded(rate),
                            rate = rate
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }
                        val netAmount = amountFiat.localFiat.nativeAmount

                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        dispatchEvent(Event.OnAmountAccepted(amountFiat, netTransferAmount = netAmount))
                        dispatchEvent(Event.ProceedWithPurchase(amountFiat))
                    }

                    is SwapPurpose.FundWithWallet -> {
                        val rate = exchange.rateForUsd()
                        // funding through external wallet — no balance cap,
                        // funds come from the external wallet not reserves
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = Fiat(data.amountData.amount, rate.currency),
                            token = Token.usdf,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }

                        dispatchEvent(Event.OnAmountAccepted(amountFiat, netTransferAmount = amountFiat.localFiat.nativeAmount))
                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        dispatchEvent(
                            Event.CreateAndSendTransactionToWallet(
                                token = stateFlow.value.tokenWithBalance!!.token,
                                amount = amountFiat
                            )
                        )
                    }

                    is SwapPurpose.Sell -> {
                        val rate = exchange.entryRate
                        val tokenWithBalance = stateFlow.value.tokenWithBalance!!
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = Fiat(data.amountData.amount, rate.currency),
                            token = tokenWithBalance.token,
                            balance = tokenWithBalance.balance,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }
                        val netAmount = stateFlow.value.netTransferAmount

                        dispatchEvent(Event.OnAmountAccepted(amountFiat, netTransferAmount = netAmount))
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
                    trackTransaction(token)
                    dispatchEvent(Event.OnPurchaseSubmitted(token, swapId))
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = true))
                    // buy submitted from reserves, drop reserves balance
                    tokenCoordinator.subtract(Token.usdf, amount.localFiat)
                }.onFailure { cause ->
                    trackTransaction(token, error = cause)
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = false))
                    if (cause is SwapError.InvalidSwap) {
                        if (cause.insufficientBalance) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
                                message = resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
                            )
                        }
                        return@onFailure
                    }

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
                // Refresh balance from network before submitting to ensure
                // the on-chain balance matches what we're about to sell.
                tokenCoordinator.updateTokenAccount(token.address)

                // underlyingTokenAmount.quarks are token quarks, not USD —
                // convert back through the bonding curve for an apples-to-apples comparison.
                val amountInUsd = Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, token)
                val refreshedBalance = tokenCoordinator.balanceForToken(token)
                if (amountInUsd > refreshedBalance) {
                    dispatchEvent(Event.UpdateSellState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds),
                    )
                    return@onEach
                }

                transactionController.sell(
                    owner = owner,
                    amount = amount,
                    of = token,
                ).onSuccess { swapId ->
                    trackTransaction(token)
                    dispatchEvent(Event.OnSellSubmitted(token, swapId))
                    dispatchEvent(Event.UpdateSellState(loading = false, success = true))
                    // sell submitted, drop from balance
                    tokenCoordinator.subtract(token, amount.localFiat)
                }.onFailure { cause ->
                    trackTransaction(token, error = cause)
                    dispatchEvent(Event.UpdateSellState(loading = false, success = false))
                    if (cause is SwapError.Denied) {
                        if (cause.amountTooLowForFee) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_sellFailedDueToBeingTooLow),
                                message = resources.getString(R.string.error_description_sellFailedDueToBeingTooLow),
                            )
                            return@onFailure
                        }
                    }
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
                    val isUsingReserves = stateFlow.value.purpose is SwapPurpose.Buy ||
                            stateFlow.value.purpose is SwapPurpose.Sell
                    viewModelScope.launch { tokenCoordinator.updateTokenAccount(token) }
                    if (isUsingReserves) {
                        viewModelScope.launch { tokenCoordinator.updateTokenAccount(Mint.usdf) }
                    }
                    viewModelScope.launch {
                        // update activity feed to grab the tx as a result of this buy/sell
                        feedCoordinator.fetchSinceLatest()
                    }
                    dispatchEvent(Event.UpdateProcessingState(loading = false, success = true))
                },
                onError = {
                    dispatchEvent(Event.UpdateProcessingState(loading = false, success = false, error = true))
                }
            ).launchIn(viewModelScope)
    }

    private fun trackTransaction(token: Token, error: Throwable? = null) {
        val purpose = stateFlow.value.purpose
        val method = when (purpose) {
            is SwapPurpose.Buy -> Analytics.SwapMethod.Buy.Reserves
            is SwapPurpose.FundWithWallet -> Analytics.SwapMethod.Buy.Phantom
            else -> Analytics.SwapMethod.Sell
        }

        when (method) {
            Analytics.SwapMethod.Sell -> {
                analytics.sell(
                    amount = stateFlow.value.netTransferAmount,
                    feeAmount = stateFlow.value.feeAmount,
                    mint = token.address,
                    error = error
                )
            }
            is Analytics.SwapMethod.Buy -> {
                analytics.buy(
                    method = method.with,
                    amount = stateFlow.value.netTransferAmount,
                    mint = token.address,
                    error = error
                )
            }
        }
    }

    override fun onCleared() {
        exchange.resetEntryToBalance()
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
                        confirmedNetTransferAmount = event.netTransferAmount,
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