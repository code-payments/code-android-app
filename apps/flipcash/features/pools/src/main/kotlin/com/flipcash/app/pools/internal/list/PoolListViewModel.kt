package com.flipcash.app.pools.internal.list

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.pools.PoolsCoordinator
import com.flipcash.app.pools.internal.list.components.PoolListItem
import com.flipcash.app.pools.internal.list.components.PoolListItem.PoolItem
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
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
            pagingData
                .map { pool -> PoolItem(pool) }
                .insertSeparators { before, after ->
                    when {
                        // Start of list and first pool is open: insert open header
                        before == null && after?.data?.pool?.isOpen == true -> PoolListItem.Header.Open
                        // Start of list and first pool is closed: insert closed header
                        before == null && after?.data?.pool?.isOpen == false -> PoolListItem.Header.Completed
                        // Transition from open to closed: insert closed header
                        before?.data?.pool?.isOpen == true && after?.data?.pool?.isOpen == false -> PoolListItem.Header.Completed
                        // No separator for same group or other cases
                        else -> null
                    }
                }
        }
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