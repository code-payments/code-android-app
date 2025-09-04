package com.flipcash.app.pools.internal.betting

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.cache.DataOrigin
import com.flipcash.app.core.extensions.mapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.pools.Empty
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolBetSummary
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.internal.PaymentError
import com.flipcash.app.payments.internal.PoolBidPaymentMetadata
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.pools.R
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.models.ClosePoolError
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.times
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

@HiltViewModel
internal class PoolBettingViewModel @Inject constructor(
    private val poolsCoordinator: PoolsCoordinator,
    userManager: UserManager,
    shareController: ShareSheetController,
    resources: ResourceHelper,
    payments: PaymentController,
    analytics: FlipcashAnalyticsService,
    exchange: Exchange,
    balanceController: BalanceController,
    onrampController: OnRampAmountController,
) : BaseViewModel2<PoolBettingViewModel.State, PoolBettingViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val loading: Boolean = false,
        val balance: LocalFiat = LocalFiat.Zero,
        val rendezvous: Ed25519.KeyPair? = null,
        val userId: ID? = null,
        val metadata: Pool = Pool.Empty,
        val bets: List<PoolBet> = emptyList(),
        val outcomes: List<PoolBetOutcome> = listOf(
            PoolBetOutcome.BooleanOutcome(true),
            PoolBetOutcome.BooleanOutcome(false),
        ),
        val selectedOutcome: PoolBetOutcome? = null,
        val isDistributed: Boolean? = null,
        val bottomBarActions: List<BottomBarAction> = emptyList(),
        val preferredOnRampProvider: OnRampProvider? = null,
    ) {
        val isLoaded: Boolean
            get() = metadata != Pool.Empty

        private val poolWithBets: PoolWithBets
            get() = PoolWithBets(
                pool = metadata,
                isHost = metadata.creator == userId,
                bets = bets,
                rendezvousSeed = rendezvous?.seed
            )

        val isHost: Boolean
            get() = poolWithBets.isHost

        val isOpen: Boolean
            get() = metadata.isOpen

        val userBet: PoolBet?
            get() = bets.find { it.userId == userId }

        val hasBoughtIn: Boolean
            get() {
                return userBet?.hasPaidForBet == true || selectedOutcome != null
            }

        val isResolved: Boolean
            get() = metadata.resolution is PoolResolution.DecisionMade

        val totalPerOutcome: Map<PoolBetOutcome, Int>
            get() {
                val betsPerOutcome = mutableMapOf<PoolBetOutcome, Int>()

                outcomes.forEach { outcome ->
                    val countForOutcome = when (val summary = poolWithBets.pool.betSummary) {
                        is PoolBetSummary.Boolean -> {
                            when (outcome) {
                                is PoolBetOutcome.BooleanOutcome -> {
                                    if (outcome.value) summary.numYes else summary.numNo
                                }

                                PoolBetOutcome.NotSet -> 0
                            }
                        }
                        PoolBetSummary.NotSet -> 0
                    }
                    betsPerOutcome[outcome] = countForOutcome
                }

                return betsPerOutcome
            }

        /**
         * Whether the user can select/change their desired outcome on this pool.
         *
         * A user is allowed to place a bet if:
         * - The pool is open
         * - The pool has not already been resolved
         * - The user has not already placed a bet on this pool
         */
        val canSelectOutcome: Boolean
            get() {
                if (!metadata.isOpen) return false
                if (metadata.resolution != PoolResolution.NotSet) return false
                if (selectedOutcome != null) return false
                return !hasBoughtIn
            }

        /**
         * The total amount in the pool.
         */
        val poolTotal: Fiat
            get() {
                val summary = metadata.betSummary
                return when (summary) {
                    is PoolBetSummary.Boolean -> {
                        val totalBets = summary.numYes + summary.numNo
                        metadata.buyIn.times(totalBets)
                    }

                    PoolBetSummary.NotSet -> Fiat.Zero
                }
            }
    }

    sealed interface Event {
        data class OnLoadingChanged(val loading: Boolean) : Event
        data class OnBalanceChanged(val balance: LocalFiat) : Event
        data class OnPoolIdChanged(val poolId: ID) : Event
        data class OnPoolRendezvousChanged(val rendezvous: Ed25519.KeyPair, val fromUser: Boolean = true) : Event
        data class OnUserIdChanged(val id: ID) : Event
        data class OnPoolLoaded(val data: PoolWithBets) : Event
        data class OnBottomBarActionsChanged(val actions: List<BottomBarAction>) : Event
        data class OnOutcomeSelected(val outcome: PoolBetOutcome) : Event
        data class OnOutcomePaidFor(val outcome: PoolBetOutcome) : Event
        data object OnDeclareOutcome : Event
        data class OnResolutionSelected(val resolution: PoolResolution.DecisionMade) : Event
        data object OnDistributeFunds : Event
        data class OnFundsDistributed(val isDistributed: Boolean) : Event
        data object OnSharePool : Event
        data object OnFailedToLoad: Event
        data class AddCashToWallet(val amount: Fiat): Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider?): Event
        data class OpenOnRampAmountModal(val amount: Fiat) : Event
        data class UpdateLoadingState(val loading: Boolean = false, val success: Boolean = false) : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        userManager.state
            .mapNotNull { it.accountId }
            .onEach {
                dispatchEvent(Event.OnUserIdChanged(it))
            }.launchIn(viewModelScope)

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
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolIdChanged>()
            .map { it.poolId }
            .map {
                poolsCoordinator.getPool(it)
            }
            .mapResult { cacheEntry ->
                when (cacheEntry.origin) {
                    DataOrigin.Cache -> {
                        trace(tag = "Pools", message = "Loaded from cache", type = TraceType.Process)
                    }
                    DataOrigin.Network -> {
                        trace(tag = "Pools", message = "Loaded from network", type = TraceType.Process)
                    }
                }
                cacheEntry.data
            }
            .onResult(
                onSuccess = { data ->
                    dispatchEvent(Event.OnPoolLoaded(data))
                    poolsCoordinator.onPoolOpened(data.pool.id)
                },
                onError = {
                    BottomBarManager.showError(
                        resources.getString(R.string.error_title_poolNotFound),
                        resources.getString(R.string.error_description_poolNotFound),
                    ) {
                        dispatchEvent(Event.OnFailedToLoad)
                    }
                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolRendezvousChanged>()
            .filter { it.fromUser }
            .map { it.rendezvous }
            .onEach { analytics.poolOpenedFromDeeplink(it.publicKeyBytes.toList()) }
            .map { rendezvous ->
                dispatchEvent(Event.OnLoadingChanged(true))
                poolsCoordinator.getPool(rendezvous)
            }
            .onResult(
                onSuccess = { data ->
                    dispatchEvent(Event.OnLoadingChanged(false))
                    dispatchEvent(Event.OnPoolLoaded(data))
                    poolsCoordinator.onPoolOpened(data.pool.id)
                },
                onError = {
                    dispatchEvent(Event.OnLoadingChanged(false))
                    BottomBarManager.showError(
                        resources.getString(R.string.error_title_poolNotFound),
                        resources.getString(R.string.error_description_poolNotFound),
                    ) {
                        dispatchEvent(Event.OnFailedToLoad)
                    }
                }
            ).launchIn(viewModelScope)

        stateFlow
            .filter { it.isLoaded }
            .mapNotNull { it.metadata.id }
            .flatMapLatest { poolsCoordinator.observePool(it) }
            .filterNotNull()
            .onEach { dispatchEvent(Event.OnPoolLoaded(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolLoaded>()
            .map { it.data }
            .onEach { data ->
                val selfBet = data.bets.find { it.userId == stateFlow.value.userId }
                if (selfBet != null && selfBet.hasPaidForBet) {
                    dispatchEvent(Event.OnOutcomePaidFor(selfBet.selectedOutcome))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolLoaded>()
            .map { it.data }
            .filter { it.isHost }
            .map { poolsCoordinator.isPoolDistributed(it.pool) }
            .onResult(
                onSuccess = { isDistributed ->
                    dispatchEvent(Event.OnFundsDistributed(isDistributed))
                },
                onError = {
                    dispatchEvent(Event.OnFundsDistributed(true))
                }
            ).launchIn(viewModelScope)

        stateFlow
            .filter { it.isLoaded }
            .distinctUntilChanged { old, new ->
                old.rendezvous == new.rendezvous && old.isHost == new.isHost && old.isDistributed == new.isDistributed
            }
            .onEach {
                val actions = buildList {
                    if (it.isHost) {
                        if (!it.isResolved) {
                            if (it.rendezvous != null) {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_sharePoolWithFriends),
                                        onClick = { dispatchEvent(Event.OnSharePool) }
                                    )
                                )
                            }
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_declareOutcome),
                                    style = BottomBarManager.BottomBarButtonStyle.Text,
                                    onClick = { dispatchEvent(Event.OnDeclareOutcome) }
                                )
                            )
                        } else if (it.isDistributed == false) {
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_distributeFunds),
                                    onClick = { dispatchEvent(Event.OnDistributeFunds) }
                                )
                            )
                        }
                    } else {
                        if (it.rendezvous != null) {
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_sharePoolWithFriends),
                                    onClick = { dispatchEvent(Event.OnSharePool) }
                                )
                            )
                        }
                    }
                }

                dispatchEvent(Event.OnBottomBarActionsChanged(actions))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSharePool>()
            .mapNotNull {
                val pool = stateFlow.value.metadata
                val rendezvous = stateFlow.value.rendezvous

                if (pool == Pool.Empty) return@mapNotNull null
                if (rendezvous == null) return@mapNotNull null
                pool to rendezvous
            }.map { Shareable.Pool(it.first, it.second) }
            .onEach { shareable ->
                shareController.present(shareable)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOutcomeSelected>()
            .map { it.outcome }
            .mapNotNull {
                val pool = stateFlow.value.metadata
                val rendezvous = stateFlow.value.rendezvous
                if (rendezvous == null) return@mapNotNull null
                PaymentRequest.PoolBid(
                    pool = pool,
                    rendezvous = rendezvous,
                    outcome = it,
                ) {
                    poolsCoordinator.placeBet(
                        poolId = pool.id,
                        rendezvous = rendezvous,
                        outcome = it,
                    )
                }
            }
            .map { request ->
                payments.requestPaymentConfirmation(request)
            }.flatMapLatest {
                payments.paymentEvents.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> {
                        when (event.error) {
                            is PaymentError.InsufficientBalance -> {
                                BottomBarManager.showMessage(
                                    resources.getString(R.string.error_title_youNeedMoreCash),
                                    resources.getString(R.string.error_description_youNeedMoreCash),
                                    type = BottomBarManager.BottomBarMessageType.ERROR,
                                    showScrim = true,
                                    showCancel = false,
                                    actions = listOf(
                                        BottomBarAction(
                                            text = resources.getString(R.string.action_addMoreCash),
                                            style = BottomBarManager.BottomBarButtonStyle.Filled,
                                        ) {
                                            viewModelScope.launch {
                                                val rate = exchange.entryRate
                                                // if we are USD we can skip the rate fetch since its 1:1
                                                if (rate.currency != CurrencyCode.USD) {
                                                    exchange.fetchRatesIfNeeded()
                                                }

                                                val buyIn = stateFlow.value.metadata.buyIn

                                                val amountFiat = LocalFiat(
                                                    usdc = buyIn.convertingTo(
                                                        exchange.rateToUsd(
                                                            rate.currency
                                                        )!!
                                                    ),
                                                    converted = buyIn,
                                                    rate = rate,
                                                )

                                                val neededAmount = amountFiat.usdc - stateFlow.value.balance.usdc
                                                dispatchEvent(Event.AddCashToWallet(neededAmount))
                                            }
                                        },
                                        BottomBarAction(
                                            text = resources.getString(R.string.action_dismiss),
                                            style = BottomBarManager.BottomBarButtonStyle.Outlined,
                                        )
                                    )
                                )
                            }
                            // other errors are handled internally
                            else -> Unit
                        }
                    }
                    is PaymentEvent.OnRpcFailure -> {
                        BottomBarManager.showError(
                            resources.getString(R.string.error_title_placeBetFailed),
                            resources.getString(R.string.error_description_placeBetFailed),
                        )
                    }
                    is PaymentEvent.OnPaymentSuccess -> {
                        event.acknowledge(true) {
                            viewModelScope.launch {
                                analytics.placedBidInPool(stateFlow.value.metadata.id)
                                poolsCoordinator.onBetPaidForInPool(
                                    poolId = (event.metadata as PoolBidPaymentMetadata).pool.id
                                )
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDeclareOutcome>()
            .onEach {
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.prompt_title_poolResolution),
                    subtitle = "",
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_yes),
                            style = BottomBarManager.BottomBarButtonStyle.Filled,
                            onClick = {
                                dispatchEvent(
                                    Event.OnResolutionSelected(
                                        PoolResolution.BooleanResolution(true)
                                    )
                                )
                            }
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_no),
                            style = BottomBarManager.BottomBarButtonStyle.Filled,
                            onClick = {
                                dispatchEvent(
                                    Event.OnResolutionSelected(
                                        PoolResolution.BooleanResolution(false)
                                    )
                                )
                            }
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_tie),
                            style = BottomBarManager.BottomBarButtonStyle.Outlined,
                            onClick = {
                                dispatchEvent(
                                    Event.OnResolutionSelected(
                                        PoolResolution.Refund
                                    )
                                )
                            }
                        ),
                    ),
                    showCancel = true,
                    showScrim = true,
                    type = BottomBarManager.BottomBarMessageType.THEMED,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDistributeFunds>()
            .filter { stateFlow.value.isResolved }
            .mapNotNull { stateFlow.value.metadata.resolution as? PoolResolution.DecisionMade }
            .mapNotNull { resolution ->
                val rendezvous = stateFlow.value.rendezvous
                if (rendezvous == null) return@mapNotNull null

                // in this flow we are already resolved so no need to call close and resolve
                PaymentRequest.ResolvePool(
                    pool = stateFlow.value.metadata,
                    bets = stateFlow.value.bets,
                    rendezvous = rendezvous,
                    resolution = resolution,
                    rpcCall = null
                )
            }.map { request -> payments.requestPaymentConfirmation(request) }
            .flatMapLatest {
                payments.paymentEvents.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> {
                        when (val error = event.error) {
                            is PaymentError.PoolDistributionFailed -> {
                                BottomBarManager.showError(
                                    title = resources.getString(R.string.error_title_resolvePoolDistributionsFailed),
                                    message = resources.getString(R.string.error_description_resolvePoolDistributionsFailed),
                                    additionalInfo = error.state
                                )
                            }
                            // other errors are handled internally
                            else -> Unit
                        }
                    }
                    is PaymentEvent.OnRpcFailure -> Unit
                    is PaymentEvent.OnPaymentSuccess -> {
                        analytics.declaredOutcomeInPool(stateFlow.value.metadata.id)
                        event.acknowledge(true) {
                            dispatchEvent(Event.OnFundsDistributed(true))
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnResolutionSelected>()
            .map { it.resolution }
            .mapNotNull { resolution ->
                val rendezvous = stateFlow.value.rendezvous
                if (rendezvous == null) return@mapNotNull null
                val pool = stateFlow.value.metadata
                PaymentRequest.ResolvePool(
                    pool = pool,
                    bets = stateFlow.value.bets,
                    rendezvous = rendezvous,
                    resolution = resolution,
                ) {
                    poolsCoordinator.closePool(
                        pool = pool,
                        rendezvous = rendezvous,
                    ).fold(
                        onSuccess = {
                            poolsCoordinator.resolvePool(
                                pool = pool,
                                rendezvous = rendezvous,
                                resolution = resolution,
                            )
                        },
                        onFailure = { Result.failure(it) }
                    )
                }
            }
            .map { request -> payments.requestPaymentConfirmation(request) }
            .flatMapLatest {
                payments.paymentEvents.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit
                    is PaymentEvent.OnRpcFailure -> {
                        when (event.error) {
                            is ClosePoolError -> {
                                BottomBarManager.showError(
                                    resources.getString(R.string.error_title_closePoolFailed),
                                    resources.getString(R.string.error_description_closePoolFailed),
                                )
                            }
                            else -> {
                                BottomBarManager.showError(
                                    resources.getString(R.string.error_title_resolvePoolFailed),
                                    resources.getString(R.string.error_description_resolvePoolFailed),
                                )
                            }
                        }

                    }
                    is PaymentEvent.OnPaymentSuccess -> {
                        event.acknowledge(true) {
                            analytics.declaredOutcomeInPool(stateFlow.value.metadata.id)
                            dispatchEvent(Event.OnFundsDistributed(true))
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.AddCashToWallet>()
            .map { it.amount }
            .onEach { amount ->
                val provider = stateFlow.value.preferredOnRampProvider
                if (provider is OnRampProvider.Coinbase && provider.type == OnRampType.Virtual) {
                    // has coinbase provider supporting google pay - pop selection for quick add
                    // has coinbase provider - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal(amount))
                } else {
                    // route to provider list
                    val origin = AppRoute.Pool.Details(
                        poolId = stateFlow.value.metadata.id,
                        rendezvous = stateFlow.value.rendezvous,
                    )
                    dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.ProviderList(origin, amount)))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OpenOnRampAmountModal>()
            .map { onrampController.requestAmountSelection(OnRampProvider.Coinbase(OnRampType.Virtual)) }
            .flatMapLatest {
                onrampController.confirmationEvents.take(1)
            }.onEach { event ->
                when (event) {
                    is ConfirmationEvent.OnConfirmationSuccess -> {
                        when (event.amount) {
                            OnRampAmount.Custom -> dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.AmountEntry))
                            is OnRampAmount.Predefined -> Unit
                        }
                    }

                    ConfirmationEvent.Cancelled -> Unit
                }
            }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        poolsCoordinator.onPoolClosed()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnLoadingChanged -> { state ->
                    state.copy(
                        loading = event.loading
                    )
                }

                is Event.OnBalanceChanged -> { state ->
                    state.copy(
                        balance = event.balance
                    )
                }

                is Event.OnUserIdChanged -> { state ->
                    state.copy(
                        userId = event.id
                    )
                }

                is Event.OnPoolIdChanged -> { state -> state }

                is Event.OnPoolRendezvousChanged -> { state ->
                    state.copy(rendezvous = event.rendezvous)
                }

                is Event.OnPoolLoaded -> { state ->
                    state.copy(
                        metadata = event.data.pool,
                        bets = event.data.bets,
                        rendezvous = event.data.rendezvous,
                    )
                }

                is Event.OnOutcomeSelected -> { state ->
                    state
                }

                is Event.OnOutcomePaidFor -> { state ->
                    state.copy(
                        selectedOutcome = event.outcome
                    )
                }

                is Event.OnSharePool -> { state -> state }

                is Event.OnDeclareOutcome -> { state -> state }
                is Event.OnResolutionSelected -> { state -> state }

                is Event.OnBottomBarActionsChanged -> { state ->
                    state.copy(
                        bottomBarActions = event.actions
                    )
                }

                is Event.OnFailedToLoad -> { state ->
                    state
                }

                is Event.OnFundsDistributed -> { state ->
                    if (state.isResolved) {
                        state.copy(
                            isDistributed = event.isDistributed
                        )
                    } else {
                        state
                    }
                }

                is Event.OnDistributeFunds -> { state -> state }
                is Event.AddCashToWallet -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state -> state.copy(preferredOnRampProvider = event.provider) }
                is Event.OpenOnRampAmountModal -> { state -> state }
                is Event.UpdateLoadingState -> { state -> state }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}