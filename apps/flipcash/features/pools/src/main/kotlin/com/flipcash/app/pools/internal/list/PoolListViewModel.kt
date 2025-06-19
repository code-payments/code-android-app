package com.flipcash.app.pools.internal.list

import com.flipcash.services.controllers.PoolController
import com.flipcash.services.models.PoolMetadata
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class PoolListViewModel @Inject constructor(
    private val poolsController: PoolController,
): BaseViewModel2<PoolListViewModel.State, PoolListViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    data class State(
        val pools: List<PoolMetadata> = emptyList()
    )

    sealed interface Event {
        data class OnPoolsUpdated(val pools: List<PoolMetadata>) : Event
        data class OnPoolClicked(val pool: PoolMetadata) : Event
        data object OnCreatePool: Event
    }

    init {

    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPoolsUpdated -> { state -> state.copy(pools = event.pools) }
                is Event.OnPoolClicked -> { state -> state }
                is Event.OnCreatePool -> { state -> state }
            }
        }
    }
}