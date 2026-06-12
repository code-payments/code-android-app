package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.to
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.CoinbaseOnRampState
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.PhantomSwapResult
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.onramp.PurchaseGate
import com.flipcash.app.onramp.isAlert
import com.flipcash.app.onramp.isNetworkCause
import com.flipcash.app.onramp.messaging
import com.getcode.manager.BottomBarAction
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
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
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

data class AmountEntryState(
    val limits: Limits? = null,
    val maxToAdd: Pair<Double, CurrencyCode>? = null,
    val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
)

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val userManager: UserManager,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    transactionController: TransactionOperations,
    private val resources: ResourceHelper,
    private val tokenCoordinator: TokenCoordinator,
    feedCoordinator: ActivityFeedCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val purchaseMethodController: PurchaseMethodController,
    private val coinbaseOnRampController: CoinbaseOnRampController,
    private val phantomWalletController: PhantomWalletController,
    dispatchers: DispatcherProvider,
) : BaseViewModel<SwapViewModel.State, SwapViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    private val styleFlow: StateFlow<AmountEntryStyle> = stateFlow.map { vmState ->
        val isBuy = vmState.purpose is SwapPurpose.Buy
        AmountEntryStyle(
            actionLabel = if (isBuy) resources.getString(R.string.action_buy) else resources.getString(R.string.action_next),
            canChangeCurrency = (vmState.purpose as? SwapPurpose.Buy)?.fundingSource != FundingSource.Phantom,
            infoHint = { resources.getString(R.string.subtitle_buySellCashHint, it) },
            overMaxHint = {
                if (vmState.purpose is SwapPurpose.BalanceIncrease)
                    resources.getString(R.string.subtitle_buyHintLimitExceeded, it)
                else resources.getString(R.string.subtitle_sellHintLimitExceeded, it)
            },
            belowMinHint = { resources.getString(R.string.subtitle_buyHintBelowMinimum, it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AmountEntryStyle(actionLabel = ""))

    private val maxAmountFlow: StateFlow<Fiat?> = combine(
        stateFlow.map { it.purpose },
        stateFlow.map { it.amountEntryState.maxToAdd },
        stateFlow.map { it.tokenBalance },
    ) { purpose, maxToAdd, tokenBalance ->
        when (purpose) {
            is SwapPurpose.Buy -> maxToAdd?.let { Fiat(it.first, it.second) }
            is SwapPurpose.Sell -> tokenBalance
            null -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val minimumAmountFlow: StateFlow<Fiat?> = stateFlow
        .map { it.minimumBuyAmount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val combinedLoadingState: StateFlow<LoadingSuccessState> = stateFlow
        .map { vmState ->
            LoadingSuccessState(
                loading = vmState.buyProgress.loading || vmState.sellProgress.loading || vmState.processingProgress.loading,
                success = vmState.buyProgress.success || vmState.sellProgress.success || vmState.processingProgress.success,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState())

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = styleFlow,
        loadingState = combinedLoadingState,
        maxAmount = maxAmountFlow,
        minimumAmount = minimumAmountFlow,
    )

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
        val confirmedEnteredAmount: Fiat? = null,
        val confirmedFeeAmount: Fiat? = null,
        val minimumBuyAmount: Fiat? = null,
        val pendingInitialAmount: Fiat? = null,
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
            get() = buyProgress.isIdle && sellProgress.isIdle && processingProgress.isIdle

        val tokenBalance: Fiat
            get() = tokenWithBalance?.balance ?: Fiat.Zero

        val reservesBalance: Fiat
            get() = reservesWithBalance?.balance ?: Fiat.Zero

        val enteredAmount: Fiat
            get() = confirmedEnteredAmount ?: Fiat.Zero

        val feeAmount: Fiat
            get() = confirmedFeeAmount ?: Fiat.Zero

        val netTransferAmount: Fiat
            get() = confirmedNetTransferAmount ?: Fiat.Zero
    }

    sealed interface Event {
        data class OnPurposeChanged(val purpose: SwapPurpose) : Event
        data class OnSelectedTokenChanged(val token: TokenWithBalance) : Event
        data class OnReservesUpdated(val reserves: TokenWithBalance) : Event

        data class OnLimitsChanged(val limits: Limits?) : Event

        // region amount entry events
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) : Event

        data class OnCurrencyChanged(val currency: Currency) : Event

        data object OtherWalletSelected: Event
        data object CoinbaseSelected : Event
        data object PhantomSelected : Event
        data object ConfirmPhantomTransaction : Event
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

        data object StartPhantomCeremony : Event
        data object PhantomConnected : Event
        data class PhantomNavigateToProcessing(val swapId: SwapId? = null) : Event
        data object PhantomCeremonyFailed : Event
        data object DepositSubmitted : Event

        data class CreateAndSendTransactionToWallet(val token: Token, val amount: VerifiedFiat) :
            Event

        data class OnAmountAccepted(
            val amount: VerifiedFiat,
            val netTransferAmount: Fiat,
            val enteredAmount: Fiat = Fiat.Zero,
            val feeAmount: Fiat = Fiat.Zero,
        ) : Event

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

        data class OnInitialAmountProvided(val amount: Fiat) : Event
        data object OnInitialAmountEntered : Event
        data class OnVerificationNeeded(val phone: Boolean, val email: Boolean) : Event
        data object Exit : Event
    }

    private val enteredAmount: Fiat
        get() {
            val delegateState = amountDelegate.state.value
            return Fiat(
                fiat = delegateState.enteredAmount,
                currencyCode = stateFlow.value.tokenBalance.currencyCode,
            )
        }

    private val feeAmount: Fiat
        get() {
            val fee = stateFlow.value.sellFee ?: return Fiat.Zero
            return enteredAmount * (fee / 100.0)
        }

    private val netTransferAmount: Fiat
        get() = stateFlow.value.confirmedNetTransferAmount ?: when (stateFlow.value.purpose) {
            is SwapPurpose.BalanceIncrease -> enteredAmount
            else -> Fiat(
                fiat = enteredAmount.decimalValue - feeAmount.decimalValue,
                currencyCode = enteredAmount.currencyCode,
            )
        }

    private val transactionLimit: Fiat
        get() = when (stateFlow.value.purpose) {
            is SwapPurpose.Buy -> {
                val sendLimit = enteredAmount.currencyCode.let {
                    stateFlow.value.amountEntryState.limits?.sendLimitFor(it)
                } ?: SendLimit.Zero
                sendLimit.maxPerDay.toFiat(enteredAmount.currencyCode)
            }
            is SwapPurpose.Sell -> stateFlow.value.tokenBalance
            null -> Fiat.Zero
        }

    val checkBalanceLimit: () -> Boolean = {
        val amount = amountDelegate.state.value.enteredAmount
        val currencyCode = amountDelegate.state.value.currency.code ?: CurrencyCode.USD
        val conversionRate = exchange.rateToUsd(currencyCode) ?: Rate.ignore
        val enteredInUsdf = Fiat(
            fiat = amount,
            currencyCode = currencyCode,
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
        val limit = transactionLimit
        val isOverLimit = enteredAmount.valueGreaterThan(limit)
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_insufficientFunds),
                resources.getString(R.string.error_description_insufficientFunds)
            )
        }
        isOverLimit
    }

    init {
        eventFlow.filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                val mint = purpose.mint

                combine(
                    tokenCoordinator.tokenBalances,
                    exchange.observePreferredRate(),
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
                        trace(
                            tag = "BuySellSwap",
                            message = "Unable to find token for mint ${mint.base58()}"
                        )
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
                    is SwapPurpose.Sell -> purpose.mint
                }

                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    exchange.observePreferredRate(),
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
            }.onEach { currency ->
                amountDelegate.onCurrencyChanged(currency)
                dispatchEvent(Event.OnCurrencyChanged(currency))
                val pending = stateFlow.value.pendingInitialAmount
                if (pending != null) {
                    amountDelegate.prefill(pending.decimalValue)
                    dispatchEvent(Event.OnInitialAmountEntered)
                }
            }.launchIn(viewModelScope)

        combine(
            tokenCoordinator.observeReservesBalance(),
            exchange.observePreferredRate(),
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

        transactionController.limits
            .onEach { dispatchEvent(Event.OnLimitsChanged(it)) }
            .launchIn(viewModelScope)

        combine(stateFlow, amountDelegate.state) { vmState, delegateState ->
            vmState.amountEntryState.limits to delegateState.currency.code
        }
            .filter { it.first != null }
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
            .map { amountDelegate.state.value }
            .filterNot {
                val purpose = stateFlow.value.purpose
                if (purpose is SwapPurpose.BalanceDecrease) {
                    checkBalanceLimit()
                } else {
                    false
                }
            }.filterNot { checkFundingAmount() }
            .mapNotNull {
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                it to purpose
            }
            .onEach { (delegateState, purpose) ->
                when (purpose) {
                    is SwapPurpose.Buy -> {
                        val rate = exchange.preferredRate
                        val currencyCode = delegateState.currency.code ?: CurrencyCode.USD
                        val conversionRate = exchange.rateToUsd(currencyCode) ?: Rate.ignore
                        val enteredInUsdf = Fiat(
                            delegateState.enteredAmount,
                            currencyCode,
                        ).convertingTo(conversionRate)
                        val reservesBalance = stateFlow.value.reservesBalance

                        if (enteredInUsdf <= reservesBalance.rounded()) {
                            // Sufficient reserves — buy directly
                            val amountFiat = verifiedFiatCalculator.compute(
                                amount = Fiat(delegateState.enteredAmount, rate.currency),
                                token = Token.usdf,
                                balance = reservesBalance.convertingToUsdIfNeeded(rate),
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
                            dispatchEvent(
                                Event.OnAmountAccepted(
                                    amountFiat,
                                    netTransferAmount = netAmount,
                                    enteredAmount = enteredAmount,
                                    feeAmount = feeAmount,
                                )
                            )
                            dispatchEvent(Event.ProceedWithPurchase(amountFiat))
                        } else {
                            // Insufficient reserves — check available purchase methods
                            val mint = purpose.mint
                            val metadata = PurchaseMethodMetadata(
                                mint = mint,
                                purchaseAmount = Fiat(delegateState.enteredAmount, rate.currency),
                                canUseOtherWallets = true, // allow external USDC deposit as a "purchase" option
                            )
                            val pinnedMethod = when (purpose.fundingSource) {
                                FundingSource.Coinbase -> PurchaseMethod.CoinbaseOnRamp
                                FundingSource.Phantom -> PurchaseMethod.PhantomWallet
                                FundingSource.Flexible -> null
                            }
                            if (pinnedMethod != null) {
                                purchaseMethodController.select(pinnedMethod, metadata)
                            } else {
                                val methods = purchaseMethodController.state.value.availableMethods
                                if (methods.size == 1) {
                                    // Single method — skip sheet, handle directly
                                    purchaseMethodController.select(methods.first(), metadata)
                                } else {
                                    purchaseMethodController.present(metadata)
                                }
                            }
                        }
                    }

                    is SwapPurpose.Sell -> {
                        val rate = exchange.preferredRate
                        val tokenWithBalance = stateFlow.value.tokenWithBalance!!
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = Fiat(delegateState.enteredAmount, rate.currency),
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
                        val netAmount = netTransferAmount

                        dispatchEvent(
                            Event.OnAmountAccepted(
                                amountFiat,
                                netTransferAmount = netAmount,
                                enteredAmount = enteredAmount,
                                feeAmount = feeAmount,
                            )
                        )
                        dispatchEvent(Event.ShowSellReceipt)
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmPhantomTransaction>()
            .onEach {
                try {
                    val delegateState = amountDelegate.state.value
                    val rate = exchange.rateForUsd()
                    val amountFiat = verifiedFiatCalculator.compute(
                        amount = Fiat(delegateState.enteredAmount, rate.currency),
                        token = Token.usdf,
                        rate = rate,
                    ).getOrElse {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_staleRates),
                            message = resources.getString(R.string.error_description_staleRates),
                        )
                        return@onEach
                    }
                    dispatchEvent(
                        Event.OnAmountAccepted(
                            amountFiat,
                            netTransferAmount = amountFiat.localFiat.nativeAmount,
                            enteredAmount = enteredAmount,
                            feeAmount = feeAmount,
                        )
                    )
                    dispatchEvent(Event.UpdateBuyState(loading = true))
                    val token = stateFlow.value.tokenWithBalance?.token ?: run {
                        handlePhantomError(IllegalStateException("Token not available"))
                        return@onEach
                    }
                    signAndSendPhantomTransaction(token, amountFiat)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handlePhantomError(e)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.StartPhantomCeremony>()
            .onEach { connectPhantomWallet() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DepositSubmitted>()
            .map { tokenCoordinator.balanceForToken(Mint.usdf).first() }
            .flatMapLatest { baseline ->
                // Observe balance — UsdcDepositSweep handles the actual
                // USDC→USDF sweep + polling, we just wait for the result.
                tokenCoordinator.balanceForToken(Mint.usdf)
                    .filter { it > baseline }
            }
            .onEach {
                feedCoordinator.fetchSinceLatest()
                dispatchEvent(Event.UpdateProcessingState(loading = false, success = true))
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
                    dispatchEvent(Event.OnSwapIdChanged(swapId))
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
                val amountInUsd =
                    Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, token)
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
                    dispatchEvent(Event.OnSwapIdChanged(swapId))
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

        eventFlow
            .filterIsInstance<Event.OnSwapIdChanged>()
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                owner to event.swapId
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
                    dispatchEvent(
                        Event.UpdateProcessingState(
                            loading = false,
                            success = false,
                            error = true
                        )
                    )
                }
            ).launchIn(viewModelScope)

        // Reset buy loading state when Coinbase payment is canceled or fails.
        // CoinbaseOnRampHandler handles error display; we just clear loading.
        coinbaseOnRampController.state
            .onEach { s ->
                when (s) {
                    is CoinbaseOnRampState.Failed,
                    CoinbaseOnRampState.Idle -> dispatchEvent(Event.UpdateBuyState())

                    is CoinbaseOnRampState.Completed,
                    is CoinbaseOnRampState.Paying -> Unit
                }
            }.launchIn(viewModelScope)

        purchaseMethodController.selections
            .onEach { (method, metadata) ->
                when (method) {
                    PurchaseMethod.CoinbaseOnRamp -> {
                        analytics.buttonTapped(Button.TokenBuyWithCoinbase)
                        dispatchEvent(Event.CoinbaseSelected)

                        val profile = userManager.profile
                        val needsPhone = profile?.verifiedPhoneNumber == null
                        val needsEmail = profile?.verifiedEmailAddress == null
                        if (needsPhone || needsEmail) {
                            dispatchEvent(Event.OnVerificationNeeded(needsPhone, needsEmail))
                            return@onEach
                        }

                        val amount = metadata.purchaseAmount ?: return@onEach

                        if (amount < minimumCoinbasePurchaseAmount) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampAmountTooLow),
                                message = resources.getString(R.string.error_description_onrampAmountTooLow),
                            )
                            return@onEach
                        }

                        val rate = exchange.preferredRate
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = amount,
                            token = Token.usdf,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }

                        dispatchEvent(
                            Event.OnAmountAccepted(
                                amountFiat,
                                netTransferAmount = amountFiat.localFiat.nativeAmount,
                                enteredAmount = enteredAmount,
                                feeAmount = feeAmount,
                            )
                        )
                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        val token = stateFlow.value.tokenWithBalance?.token ?: return@onEach

                        executeCoinbasePurchase(amountFiat, token)
                    }

                    is PurchaseMethod.CashReserves -> {
                        dispatchEvent(Event.OnAmountConfirmed)
                    }

                    PurchaseMethod.PhantomWallet -> {
                        analytics.buttonTapped(Button.TokenBuyWithPhantom)
                        dispatchEvent(Event.PhantomSelected)
                    }

                    PurchaseMethod.OtherWallet -> {
                        analytics.buttonTapped(Button.TokenBuyWithOtherWallet)
                        dispatchEvent(Event.OtherWalletSelected)
                    }
                }
            }.launchIn(viewModelScope)
    }

    private suspend fun executeCoinbasePurchase(
        amountFiat: VerifiedFiat,
        token: Token,
    ) {
        coinbaseOnRampController.checkPurchaseGates()
            .fold(
                onSuccess = { proceedWithCoinbasePurchase(amountFiat, token) },
                onFailure = { gate ->
                    dispatchEvent(Event.UpdateBuyState())
                    when (gate) {
                        is PurchaseGate.GooglePayNotSupported -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampGooglePayNotSupported),
                                message = resources.getString(R.string.error_description_onrampGooglePayNotSupported),
                            )
                        }

                        is PurchaseGate.GooglePayNoPaymentMethod -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
                            )
                        }

                        is PurchaseGate.WebViewWarning -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampNonStableWebView),
                                message = resources.getString(
                                    R.string.error_description_onrampNonStableWebView,
                                    gate.channel.name,
                                ),
                                actions = listOf(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_cancel),
                                        style = BottomBarManager.BottomBarButtonStyle.Filled,
                                    ) { /* dismiss — loading already cleared */ },
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_continueAnyway),
                                        style = BottomBarManager.BottomBarButtonStyle.Text,
                                    ) {
                                        viewModelScope.launch {
                                            dispatchEvent(Event.UpdateBuyState(loading = true))
                                            proceedWithCoinbasePurchase(amountFiat, token)
                                        }
                                    },
                                ),
                            )
                        }
                    }
                },
            )
    }

    private suspend fun proceedWithCoinbasePurchase(
        amountFiat: VerifiedFiat,
        token: Token,
    ) {
        coinbaseOnRampController.placeOrderAndStartPayment(
            token = token,
            verifiedFiat = amountFiat,
        ).onSuccess {
            trackTransaction(token)
        }.onFailure { error ->
            dispatchEvent(Event.UpdateBuyState())
            when (error) {
                is OnRampAuthError.CoinbasePhoneVerificationRequired ->
                    dispatchEvent(Event.OnVerificationNeeded(phone = true, email = false))

                is OnRampAuthError.VerificationRequired ->
                    dispatchEvent(Event.OnVerificationNeeded(error.phone, error.email))

                else -> {
                    trackTransaction(token, error = error)
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }
        }
    }

    private fun trackTransaction(token: Token, error: Throwable? = null) {
        val method = when (val purpose = stateFlow.value.purpose) {
            is SwapPurpose.Buy -> when (purpose.fundingSource) {
                FundingSource.Phantom -> Analytics.SwapMethod.Buy.Phantom
                FundingSource.Coinbase -> Analytics.SwapMethod.Buy.Coinbase
                else -> Analytics.SwapMethod.Buy.Reserves
            }

            else -> Analytics.SwapMethod.Sell
        }

        when (method) {
            Analytics.SwapMethod.Sell -> {
                analytics.sell(
                    amount = netTransferAmount,
                    feeAmount = feeAmount,
                    mint = token.address,
                    error = error
                )
            }

            is Analytics.SwapMethod.Buy -> {
                analytics.buy(
                    method = method.with,
                    amount = netTransferAmount,
                    mint = token.address,
                    error = error
                )
            }
        }
    }

    private fun connectPhantomWallet() {
        viewModelScope.launch {
            dispatchEvent(Event.UpdateBuyState(loading = true))
            phantomWalletController
                .connectWallet()
                .onSuccess {
                    dispatchEvent(Event.UpdateBuyState(success = true))
                    dispatchEvent(Event.PhantomConnected)
                    dispatchEvent(Event.UpdateBuyState())
                    analytics.connectWallet(OnRampProvider.Phantom)
                }.onFailure {
                    dispatchEvent(Event.UpdateBuyState())
                    handlePhantomError(it)
                }
        }
    }

    private fun signAndSendPhantomTransaction(
        token: Token,
        amount: VerifiedFiat,
    ) {
        viewModelScope.launch {
            phantomWalletController.executeSwap(
                amount = amount,
                fee = LocalFiat.Zero,
                token = token,
                onBeforeSign = {
                    analytics.amountSelectedForWalletTransfer(
                        OnRampProvider.Phantom,
                        amount.localFiat.underlyingTokenAmount
                    )
                },
            ).onSuccess { result ->
                when (result) {
                    is PhantomSwapResult.WithSwapId -> {
                        dispatchEvent(Event.UpdateProcessingState(loading = true))
                        dispatchEvent(Event.PhantomNavigateToProcessing(result.swapId))
                        dispatchEvent(Event.OnSwapIdChanged(result.swapId))
                    }
                    is PhantomSwapResult.DepositCompleted -> {
                        dispatchEvent(Event.UpdateProcessingState(loading = true))
                        dispatchEvent(Event.PhantomNavigateToProcessing())
                        dispatchEvent(Event.DepositSubmitted)
                    }
                }
            }.onFailure { error ->
                handlePhantomError(error)
            }
        }
    }

    private fun handlePhantomError(error: Throwable) {
        val deeplinkError = error as? DeeplinkOnRampError
            ?: DeeplinkOnRampError.FailedToCreateTransaction(message = error.message, cause = error)

        if (deeplinkError is DeeplinkOnRampError.WalletProvidedError && deeplinkError.code == DeeplinkError.UserRejectedRequest.code) {
            analytics.walletTransactionCancelled(OnRampProvider.Phantom)
        } else if (deeplinkError is DeeplinkOnRampError.FailedToSendTransaction) {
            analytics.walletTransactionFailed(OnRampProvider.Phantom)
        }

        trace(
            tag = "BuySellSwap",
            message = "Something went wrong during phantom onramp",
            type = TraceType.Error,
            metadata = {
                "errorMessage" to deeplinkError.message
                "code" to deeplinkError.code
            },
            error = deeplinkError.takeUnless { it.isAlert }
        )

        val (title, message) = deeplinkError.messaging(
            resources::getString,
            resources.getString(R.string.label_phantom)
        )

        when {
            deeplinkError.isNetworkCause -> {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_noInternet),
                    message = resources.getString(R.string.error_description_noInternet),
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }

            deeplinkError.isAlert -> {
                BottomBarManager.showAlert(
                    title = title,
                    message = message,
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }

            else -> {
                BottomBarManager.showError(
                    title = title,
                    message = message,
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }
        }

        dispatchEvent(Event.UpdateBuyState())
    }


    private val minimumCoinbasePurchaseAmount = 5.toFiat()

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
                        confirmedEnteredAmount = event.enteredAmount,
                        confirmedFeeAmount = event.feeAmount,
                        amountEntryState = entryState.copy(
                            selectedAmount = event.amount,
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

                is Event.OnCurrencyChanged -> { state -> state }

                is Event.OnMaxDetermined -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            maxToAdd = event.max to event.currencyCode
                        ),
                    )
                }

                Event.CoinbaseSelected -> { state ->
                    val purpose = state.purpose
                    if (purpose is SwapPurpose.Buy) {
                        state.copy(purpose = purpose.copy(fundingSource = FundingSource.Coinbase))
                    } else {
                        state
                    }
                }

                Event.PhantomSelected -> { state ->
                    val purpose = state.purpose
                    if (purpose is SwapPurpose.Buy) {
                        state.copy(purpose = purpose.copy(fundingSource = FundingSource.Phantom))
                    } else {
                        state
                    }
                }

                Event.OtherWalletSelected,
                Event.ConfirmPhantomTransaction,
                Event.StartPhantomCeremony,
                Event.PhantomConnected,
                is Event.PhantomNavigateToProcessing,
                Event.PhantomCeremonyFailed,
                Event.DepositSubmitted,
                Event.OnAmountConfirmed -> { state -> state }

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
                is Event.OnInitialAmountProvided -> { state ->
                    state.copy(minimumBuyAmount = event.amount, pendingInitialAmount = event.amount)
                }

                Event.OnInitialAmountEntered -> { state ->
                    state.copy(pendingInitialAmount = null)
                }

                is Event.OnVerificationNeeded -> { state -> state }
                Event.Exit -> { state -> state }
            }
        }
    }
}