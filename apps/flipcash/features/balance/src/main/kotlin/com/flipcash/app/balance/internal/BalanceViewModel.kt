package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.ActivityFeedType
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    balanceController: BalanceController,
    activityFeedController: ActivityFeedController,
): BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val balance: LocalFiat? = null,
        val feed: List<ActivityFeedMessage> = emptyList(),
    )

    sealed interface Event {
        data class OnBalanceUpdated(val balance: LocalFiat): Event
        data object UpdateFeed: Event
        data class OnMessagesUpdated(val latest: List<ActivityFeedMessage>): Event
    }

    init {
        balanceController.balance
            .onEach { dispatchEvent(Event.OnBalanceUpdated(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UpdateFeed>()
            .map { activityFeedController.getLatestMessagesFor(type = ActivityFeedType.TransactionHistory) }
            .onResult(
                onError = {

                },
                onSuccess = {
                    dispatchEvent(Event.OnMessagesUpdated(it))
                }
            ).launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.UpdateFeed -> { state -> state }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
                is Event.OnMessagesUpdated -> { state -> state.copy(feed = event.latest) }
            }
        }
    }
}