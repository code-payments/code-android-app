package com.flipcash.app.pools.internal.betting

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.pools.Empty
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolBetSummary
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.pools.R
import com.flipcash.services.user.UserManager
import com.getcode.ed25519.Ed25519
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.times
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
internal class PoolBettingViewModel @Inject constructor(
    poolsCoordinator: PoolsCoordinator,
    userManager: UserManager,
    shareController: ShareSheetController,
    resources: ResourceHelper,
    payments: PaymentController,
) : BaseViewModel2<PoolBettingViewModel.State, PoolBettingViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val loading: Boolean = false,
        val rendezvous: Ed25519.KeyPair? = null,
        val userId: ID? = null,
        val metadata: Pool = Pool.Empty,
        val bets: List<PoolBet> = emptyList(),
        val outcomes: List<PoolBetOutcome> = listOf(
            PoolBetOutcome.BooleanOutcome(true),
            PoolBetOutcome.BooleanOutcome(false),
        ),
        val selectedOutcome: PoolBetOutcome? = null,
        val bottomBarActions: List<BottomBarAction> = emptyList()
    ) {
        val isLoaded: Boolean
            get() = metadata != Pool.Empty

        private val poolWithBets: PoolWithBets
            get() = PoolWithBets(
                pool = metadata,
                rendezvous = rendezvous,
                isHost = metadata.creator == userId,
                bets = bets
            )

        val isHost: Boolean
            get() = poolWithBets.isHost

        val isOpen: Boolean
            get() = metadata.isOpen

        val userBet: PoolBet?
            get() = bets.find { it.userId == userId }

        val hasBoughtIn: Boolean
            get() {
                return userBet != null || selectedOutcome != null
            }

        val isResolved: Boolean
            get() = metadata.resolution != PoolResolution.NotSet

        val totalPerOutcome: Map<PoolBetOutcome, Fiat>
            get() {
                val betsPerOutcome = mutableMapOf<PoolBetOutcome, Fiat>()

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
                    betsPerOutcome[outcome] = metadata.buyIn.times(countForOutcome)
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
        data class OnPoolIdChanged(val poolId: ID) : Event
        data class OnPoolRendezvousChanged(val rendezvous: Ed25519.KeyPair) : Event
        data class OnUserIdChanged(val id: ID) : Event
        data class OnPoolLoaded(val data: PoolWithBets) : Event
        data class OnBottomBarActionsChanged(val actions: List<BottomBarAction>) : Event
        data class OnOutcomeSelected(val outcome: PoolBetOutcome) : Event
        data class OnOutcomePaidFor(val outcome: PoolBetOutcome) : Event
        data object OnDeclareOutcome : Event
        data class OnResolutionSelected(val resolution: PoolResolution.DecisionMade) : Event
        data object OnSharePool : Event
        data object OnFailedToLoad: Event
        data object OnMissingKeys: Event
    }

    init {
        userManager.state
            .mapNotNull { it.accountId }
            .onEach {
                dispatchEvent(Event.OnUserIdChanged(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolIdChanged>()
            .map { it.poolId }
            .map {
                dispatchEvent(Event.OnLoadingChanged(true))
                poolsCoordinator.getPool(it)
            }
            .onResult(
                onSuccess = { data ->
                    dispatchEvent(Event.OnLoadingChanged(false))
                    dispatchEvent(Event.OnPoolLoaded(data))
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

        eventFlow
            .filterIsInstance<Event.OnPoolRendezvousChanged>()
            .map { it.rendezvous }
            .map { rendezvous ->
                dispatchEvent(Event.OnLoadingChanged(true))
                poolsCoordinator.getPool(rendezvous)
            }
            .onResult(
                onSuccess = { data ->
                    dispatchEvent(Event.OnLoadingChanged(false))
                    dispatchEvent(Event.OnPoolLoaded(data))
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

        eventFlow
            .filterIsInstance<Event.OnPoolRendezvousChanged>()
            .map { it.rendezvous.publicKeyBytes.toList() }
            .flatMapLatest { poolsCoordinator.observePool(it) }
            .filterNotNull()
            .onEach {
                dispatchEvent(Event.OnPoolLoaded(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolLoaded>()
            .map { it.data }
            .onEach { data ->
                val selfBet = data.bets.find { it.userId == stateFlow.value.userId }
                if (selfBet != null /* && selfBet.hasPaidForBet */) {
                    dispatchEvent(Event.OnOutcomePaidFor(selfBet.selectedOutcome))
                }
            }.launchIn(viewModelScope)



        eventFlow
            .filterIsInstance<Event.OnPoolLoaded>()
            .map { it.data }
            .onEach { data ->
                val actions = buildList<BottomBarAction> {
                    if (data.isHost) {
                        if (data.bets.count() >= 2) {
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_declareOutcome),
                                    style = BottomBarManager.BottomBarButtonStyle.Filled,
                                    onClick = { dispatchEvent(Event.OnDeclareOutcome) }
                                )
                            )
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_sharePoolWithFriends),
                                    style = BottomBarManager.BottomBarButtonStyle.Text,
                                    onClick = { dispatchEvent(Event.OnSharePool) }
                                )
                            )
                        } else {
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_sharePoolWithFriends),
                                    onClick = { dispatchEvent(Event.OnSharePool) }
                                )
                            )
                            if (data.bets.isNotEmpty()) {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_declareOutcome),
                                        style = BottomBarManager.BottomBarButtonStyle.Text,
                                        onClick = { dispatchEvent(Event.OnDeclareOutcome) }
                                    )
                                )
                            }
                        }
                    } else {
                        add(
                            BottomBarAction(
                                text = resources.getString(R.string.action_sharePoolWithFriends),
                                onClick = { dispatchEvent(Event.OnSharePool) }
                            )
                        )
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
                val rendezvous = stateFlow.value.rendezvous ?: return@mapNotNull null
                PaymentRequest.PoolBid(
                    pool = stateFlow.value.metadata,
                    rendezvous = rendezvous,
                    outcome = it,
                ) {
                    poolsCoordinator.placeBet(
                        poolId = stateFlow.value.metadata.id,
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
                    is PaymentEvent.OnPaymentError -> Unit
                    is PaymentEvent.OnRpcFailure -> {
                        BottomBarManager.showError(
                            resources.getString(R.string.error_title_placeBetFailed),
                            resources.getString(R.string.error_description_placeBetFailed),
                        )
                    }
                    is PaymentEvent.OnPaymentSuccess -> {
                        event.acknowledge(true) { }
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
            .filterIsInstance<Event.OnResolutionSelected>()
            .map { it.resolution }
            .mapNotNull {
                val rendezvous = stateFlow.value.rendezvous ?: return@mapNotNull null

                PaymentRequest.ResolvePool(
                    pool = stateFlow.value.metadata,
                    bets = stateFlow.value.bets,
                    rendezvous = rendezvous,
                    resolution = it,
                ) {
                    poolsCoordinator.resolvePool(
                        pool = stateFlow.value.metadata,
                        resolution = it,
                        rendezvous = rendezvous
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
                        BottomBarManager.showError(
                            resources.getString(R.string.error_title_resolvePoolFailed),
                            resources.getString(R.string.error_description_resolvePoolFailed),
                        )
                    }
                    is PaymentEvent.OnPaymentSuccess -> {
                        event.acknowledge(true) {

                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnLoadingChanged -> { state ->
                    state.copy(
                        loading = event.loading
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
                    val existingRendezvous = state.rendezvous
                    state.copy(
                        metadata = event.data.pool,
                        rendezvous = existingRendezvous ?: event.data.rendezvous,
                        bets = event.data.bets,
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

                is Event.OnMissingKeys -> { state ->
                    state
                }
            }
        }
    }
}