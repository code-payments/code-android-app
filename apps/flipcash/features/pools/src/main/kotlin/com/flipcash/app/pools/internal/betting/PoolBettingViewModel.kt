package com.flipcash.app.pools.internal.betting

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.pools.Empty
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PoolPaymentMetadata
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
        val poolId: ID? = null,
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
        val isHost: Boolean
            get() = metadata.creator == userId

        val isOpen: Boolean
            get() = metadata.isOpen

        val hasBoughtIn: Boolean
            get() {
                return bets.any { it.userId == userId } || selectedOutcome != null
            }

        val isResolved: Boolean
            get() = metadata.resolution != PoolResolution.NotSet

        val betsPerOutcome: Map<PoolBetOutcome, Fiat>
            get() {
                val betsPerOutcome = mutableMapOf<PoolBetOutcome, Fiat>()
                outcomes.forEach { outcome ->
                    val countForOutcome = bets.count { it.selectedOutcome == outcome }
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
                return bets.none { it.userId == userId }
            }

        /**
         * The total amount in the pool.
         */
        val poolTotal: Fiat
            get() = metadata.buyIn.times(bets.count())
    }

    sealed interface Event {
        data class OnLoadingChanged(val loading: Boolean) : Event
        data class OnPoolIdChanged(val id: ID) : Event
        data class OnUserIdChanged(val id: ID) : Event
        data class OnPoolLoaded(val data: PoolWithBets) : Event
        data class OnBottomBarActionsChanged(val actions: List<BottomBarAction>) : Event
        data class OnOutcomeSelected(val outcome: PoolBetOutcome) : Event
        data class OnOutcomePaidFor(val outcome: PoolBetOutcome) : Event
        data object OnDeclareOutcome: Event
        data class OnResolutionSelected(val resolution: PoolResolution) : Event
        data object OnSharePool : Event
    }

    init {
        userManager.state
            .mapNotNull { it.accountId }
            .onEach {
                dispatchEvent(Event.OnUserIdChanged(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolIdChanged>()
            .map { it.id }
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
                    )
                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPoolIdChanged>()
            .flatMapLatest { poolsCoordinator.observePool(it.id) }
            .filterNotNull()
            .onEach {
                dispatchEvent(Event.OnPoolLoaded(it))
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
                if (pool == Pool.Empty) return@mapNotNull null
                pool
            }.map { Shareable.Pool(it) }
            .onEach { shareable ->
                shareController.present(shareable)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOutcomeSelected>()
            .map { it.outcome }
            .map {
                PaymentRequest.Pool(
                    pool = stateFlow.value.metadata,
                    outcome = it,
                )
            }
            .map { request ->
                payments.presentPublicPaymentConfirmation(request)
            }.flatMapLatest {
                payments.eventFlow.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit
                    is PaymentEvent.OnPaymentSuccess -> {
                        val poolMetadata = event.metadata as PoolPaymentMetadata
                        poolsCoordinator.placeBet(
                            poolId = stateFlow.value.metadata.id,
                            rendezvous = stateFlow.value.metadata.rendezvous ?: Ed25519.createKeyPair(), // will fail but better than an NPE
                            outcome = poolMetadata.selectedOutcome,
                        ).onSuccess {
                            event.acknowledge(true) {
                                dispatchEvent(Event.OnOutcomePaidFor(poolMetadata.selectedOutcome))
                            }
                        }.onFailure {
                            event.acknowledge(false) {
                                BottomBarManager.showError(
                                    resources.getString(R.string.error_title_placeBetFailed),
                                    resources.getString(R.string.error_description_placeBetFailed),
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

                            }
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_no),
                            style = BottomBarManager.BottomBarButtonStyle.Filled,
                            onClick = {

                            }
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_tie),
                            style = BottomBarManager.BottomBarButtonStyle.Outlined,
                            onClick = {

                            }
                        ),
                    ),
                    showCancel = true,
                    showScrim = true,
                    type = BottomBarManager.BottomBarMessageType.THEMED,
                )
            }.launchIn(viewModelScope)
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

                is Event.OnPoolIdChanged -> { state ->
                    state.copy(
                        poolId = event.id
                    )
                }

                is Event.OnPoolLoaded -> { state ->
                    state.copy(
                        metadata = event.data.pool,
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
            }
        }
    }
}