package com.getcode.view.main.chat.list

import androidx.paging.PagingData
import androidx.paging.map
import com.getcode.model.Conversation
import com.getcode.network.ConversationController
import com.getcode.network.TipController
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    conversationController: ConversationController,
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


    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.Noop -> { state -> state }
            }
        }
    }
}

data class ConversationWithMetadata(
    val conversation: Conversation,
    val image: String?,
    val latestMessage: String?,
    val latestMessageMillis: Long?
)