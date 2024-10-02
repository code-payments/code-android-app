package com.getcode.view.main.chat.list

import androidx.lifecycle.viewModelScope
import com.getcode.model.chat.ConversationEntity
import com.getcode.network.ConversationListController
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    conversationsController: ConversationListController,
    networkObserver: NetworkConnectivityListener,
): BaseViewModel2<ChatListViewModel.State, ChatListViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val loading: Boolean = false,
        val conversations: List<ConversationEntity> = emptyList(),
    )

    sealed interface Event {
        data class OnChatsLoading(val loading: Boolean) : Event
        data class OnChatsUpdated(val chats: List<ConversationEntity>) : Event
        data object OnOpened: Event
    }

    init {
        conversationsController.observeConversations()
            .onEach {
                if (it == null || (it.isEmpty() && !networkObserver.isConnected)) {
                    dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(true))
                }
            }
            .map { conversations ->
                when {
                    conversations == null -> null // await for confirmation it's empty
                    conversations.isEmpty() && !networkObserver.isConnected -> null // remain loading while disconnected
                    conversationsController.isLoading -> null // remain loading while fetching messages
                    else -> conversations
                }
            }
            .filterNotNull()
            .onEach { update ->
                dispatchEvent(Dispatchers.Main, Event.OnChatsUpdated(update))
            }.onEach {
                dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(false))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpened>()
            .onEach { conversationsController.fetchChats() }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOpened -> { state -> state }
                is Event.OnChatsLoading -> { state -> state.copy(loading = event.loading) }
                is Event.OnChatsUpdated -> { state -> state.copy(conversations = event.chats) }
            }
        }
    }
}