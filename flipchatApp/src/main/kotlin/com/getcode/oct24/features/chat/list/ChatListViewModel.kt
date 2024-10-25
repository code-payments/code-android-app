package com.flipchat.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatsController: ChatsController,
    networkObserver: NetworkConnectivityListener,
): BaseViewModel2<ChatListViewModel.State, ChatListViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val networkConnected: Boolean = true,
    )

    sealed interface Event {
        data class OnNetworkChanged(val connected: Boolean): Event
        data object OnOpen: Event
    }

    init {
        networkObserver.state
            .map { it.connected }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnNetworkChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpen>()
            // TODO: reopen stream once RPCs are ready
//            .onEach { chatsController.openEventStream(viewModelScope) }
            .launchIn(viewModelScope)

    }

    val chats: Flow<PagingData<Conversation>> get() = chatsController.chats.flow

    override fun onCleared() {
        super.onCleared()
//        chatsController.closeEventStream()
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOpen -> { state -> state }
                is Event.OnNetworkChanged -> { state ->
                    state.copy(networkConnected = event.connected)
                }
            }
        }
    }
}