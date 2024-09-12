package com.getcode.view.main.chat.list

import com.getcode.network.ConversationListController
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    conversationsController: ConversationListController,
): BaseViewModel2<ChatListViewModel.State, ChatListViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val x: String = ""
    )

    sealed interface Event {
        data object Noop: Event
    }

    val conversations = conversationsController.observeConversations()

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.Noop -> { state -> state }
            }
        }
    }
}