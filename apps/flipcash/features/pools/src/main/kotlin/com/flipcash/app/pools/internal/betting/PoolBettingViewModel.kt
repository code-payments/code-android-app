package com.flipcash.app.pools.internal.betting

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBet
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.PoolResolution
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.times
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class PoolBettingViewModel @Inject constructor(
    poolsCoordinator: PoolsCoordinator,
    userManager: UserManager,
): BaseViewModel2<PoolBettingViewModel.State, PoolBettingViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val poolId: ID? = null,
        val userId: ID? = null,
        val metadata: Pool = Pool.Empty,
        val bets: List<PoolBet> = emptyList(),
        val outcomes: List<PoolBetOutcome> = listOf(
            PoolBetOutcome.BooleanOutcome(true),
            PoolBetOutcome.BooleanOutcome(false),
        ),
        val selectedOutcome: PoolBetOutcome? = null,
    ) {
        val isHost: Boolean
            get() = metadata.creator == userId

        val isOpen: Boolean
            get() = metadata.isOpen

        val hasBoughtIn: Boolean
            get() {
                return bets.any { it.userId == userId }
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
        data class OnPoolIdChanged(val id: ID): Event
        data class OnUserIdChanged(val id: ID): Event
        data class OnPoolLoaded(val data: PoolWithBets): Event
        data class OnOutcomeSelected(val outcome: PoolBetOutcome): Event
        data object OnSharePool: Event
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
            .map { poolsCoordinator.getPool(it) }
            .onResult(
                onSuccess = { event ->
                    dispatchEvent(Event.OnPoolLoaded(event))
                },
                onError = {

                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOutcomeSelected>()
            .onEach {
                dispatchEvent(Event.OnOutcomeSelected(it.outcome))
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
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
                    state.copy(
                        selectedOutcome = event.outcome
                    )
                }

                is Event.OnSharePool -> { state -> state }
            }
        }
    }
}