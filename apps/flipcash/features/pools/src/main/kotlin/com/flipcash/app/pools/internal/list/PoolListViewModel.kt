package com.flipcash.app.pools.internal.list

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.services.controllers.PoolController
import com.flipcash.services.models.PoolMetadata
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class PoolListViewModel @Inject constructor(
    poolsCoordinator: PoolsCoordinator,
) : BaseViewModel2<PoolListViewModel.State, PoolListViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {
    data class State(
        val stub: String = "",
    )

    sealed interface Event {
        data class OnPoolClicked(val pool: Pool) : Event
        data object OnCreatePool : Event
    }

    val pools = poolsCoordinator.pools
        .map { pagingData ->
            pagingData.insertSeparators { before, after ->
                return@insertSeparators null
            }
        }.cachedIn(viewModelScope)

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPoolClicked -> { state -> state }
                is Event.OnCreatePool -> { state -> state }
            }
        }
    }
}