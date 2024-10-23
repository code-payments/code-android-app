package com.flipchat.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
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
    class State()

    sealed interface Event {
        data object OnOpened: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnOpened>()
            .onEach { chatsController.fetch() }
            .launchIn(viewModelScope)
    }

    val chats get() = chatsController.chats.flow

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOpened -> { state -> state }
            }
        }
    }
}