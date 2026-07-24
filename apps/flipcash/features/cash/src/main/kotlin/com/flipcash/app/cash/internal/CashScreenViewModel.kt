package com.flipcash.app.cash.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.cash.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharingStarted
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
internal class CashScreenViewModel @Inject constructor(
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    tokenCoordinator: TokenCoordinator,
    transactionController: TransactionOperations,
    analytics: FlipcashAnalyticsService,
    dispatchers: DispatcherProvider,
) : BaseViewModel<CashScreenViewModel.State, CashScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    private val maxForGiveFlow: kotlinx.coroutines.flow.StateFlow<Fiat?> = stateFlow
        .filter { it.limits != null }
        .map { it.limits to (it.token?.balance ?: LocalFiat.Zero) }
        .map { (limits, balance) ->
            val sendLimit = limits?.sendLimitFor(balance.rate.currency) ?: SendLimit.Zero
            val nextTransactionLimit = sendLimit.nextTransaction
            val max = min(nextTransactionLimit, balance.nativeAmount.decimalValue)
            Fiat(max, balance.rate.currency)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_next)),
            infoHint = { resources.getString(R.string.subtitle_giveCashHint, it) },
            overMaxHint = { resources.getString(R.string.subtitle_giveCashHintLimitExceeded, it) },
        ),
        loadingState = stateFlow.map { it.generatingBill }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
        maxAmount = maxForGiveFlow,
        tokenChanges = tokenCoordinator.observeSelectedTokenMint(),
    )
    private val tokenInitialized = CompletableDeferred<Mint?>()

    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val token: TokenWithLocalizedBalance? = null,
        val currencyModel: CurrencyHolder = CurrencyHolder(),
        val limits: Limits? = null,
        val generatingBill: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        data class InitializeToken(val mint: Mint?) : Event
        data class OnTokenSelected(val address: Mint) : Event
        data class OnTokenUpdated(val token: TokenWithLocalizedBalance) : Event
        data class OnCurrencyChanged(val model: com.getcode.opencode.model.financial.Currency) : Event
        data class OnLimitsChanged(val limits: Limits?) : Event
        data object OnGive : Event
        data class PresentBill(val bill: Scannable.Payable) : Event

        data class AddCashToWallet(val amount: Fiat) : Event
        data class UpdateLoadingState(val loading: Boolean = false, val success: Boolean = false) :
            Event

        data class OpenScreen(val screen: AppRoute) : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        // this balance check differs from withdrawal due to the fact this is a localized check
        // whereas withdrawal is USD locked
        val amount = amountDelegate.state.value.enteredAmount
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
                            val rate = exchange.preferredRate
                            val (token, balance) = stateFlow.value.token!!
                            val amountFiat = verifiedFiatCalculator.compute(
                                amount =  Fiat(amount, rate.currency),
                                token = token,
                                rate = rate,
                            ).getOrElse {
                                BottomBarManager.showAlert(
                                    title = resources.getString(R.string.error_title_staleRates),
                                    message = resources.getString(R.string.error_description_staleRates),
                                )
                                return@launch
                            }

                            val neededAmount = amountFiat.localFiat.nativeAmount - tokenBalance
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
        val amount = amountDelegate.state.value.enteredAmount
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
            }.filterNotNull()
            .onEach {
                dispatchEvent(Event.OnTokenUpdated(it))
            }.mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.rate.currency.name)
            }.onEach {
                dispatchEvent(Event.OnCurrencyChanged(it))
                amountDelegate.onCurrencyChanged(it)
            }.launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.OnLimitsChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnGive>()
            .filter { !(checkBalanceLimit() || checkSendLimit()) }
            .onEach {
                dispatchEvent(Event.UpdateLoadingState(loading = true))
                val (token, balance) = stateFlow.value.token!!
                val rate = exchange.preferredRate
                val amount = amountDelegate.state.value.enteredAmount

                val result = verifiedFiatCalculator.compute(
                    amount = Fiat(amount, rate.currency),
                    token = token,
                    balance = balance.underlyingTokenAmount,
                    rate = rate,
                ).getOrElse { error ->
                    dispatchEvent(Event.UpdateLoadingState(loading = false))
                    val (title, message) = when (error) {
                        is ComputeVerifiedFiatError.AmountBelowMinimum -> {
                            R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                        }
                        else -> {
                            R.string.error_title_staleRates to R.string.error_description_staleRates
                        }
                    }
                    BottomBarManager.showAlert(
                        title = resources.getString(title),
                        message = resources.getString(message),
                    )
                    return@onEach
                }

                val bill = Scannable.Payable.forToken(
                    token = stateFlow.value.token!!.token,
                    amount = result.localFiat,
                    verifiedState = result.verifiedState,
                )

                dispatchEvent(Event.UpdateLoadingState(loading = false, success = true))
                dispatchEvent(Event.PresentBill(bill))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.AddCashToWallet>()
            .map { it.amount }
            .onEach { shortfall ->
                // route directly to the swap amount screen, skipping token info
                val mint = stateFlow.value.selectedTokenAddress!!
                if (mint == Mint.usdf) {
                    analytics.addMoneyOpened(Analytics.AddMoneySource.GiveShortfall)
                }
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.Token.Swap(
                            purpose = SwapPurpose.Buy(mint),
                            shortfall = shortfall,
                        ),
                    )
                )
            }.launchIn(viewModelScope)
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

                is Event.InitializeToken -> { state -> state }

                is Event.OpenScreen -> { state -> state }

                Event.OnGive,
                is Event.PresentBill -> { state -> state }

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

                is Event.OnLimitsChanged -> { state -> state.copy(limits = event.limits) }
            }
        }
    }
}