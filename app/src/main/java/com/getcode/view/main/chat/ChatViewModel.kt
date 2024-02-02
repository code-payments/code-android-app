package com.getcode.view.main.chat

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.insertHeaderItem
import androidx.paging.insertSeparators
import androidx.paging.map
import com.getcode.model.ChatMessage
import com.getcode.model.ID
import com.getcode.model.Title
import com.getcode.network.HistoryController
import com.getcode.util.formatRelatively
import com.getcode.util.toInstantFromMillis
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject

sealed class ChatItem(val key: Any) {
    data class Message(val message: ChatMessage): ChatItem(message.id)
    data class Date(val date: String): ChatItem(date)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    historyController: HistoryController,
) : BaseViewModel2<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(null, null, false),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val chatId: ID?,
        val title: Title?,
        val isMuted: Boolean,
    )

    sealed interface Event {
        data class OnChatIdChanged(val id: ID): Event
        data class OnChatChanged(val title: Title?): Event
        data object OnMuteToggled: Event
    }

    init {
        stateFlow
            .map { it.chatId }
            .filterNotNull()
            .flatMapLatest { historyController.chats }
            .flowOn(Dispatchers.IO)
            .filterNotNull()
            .mapNotNull { it.firstOrNull { it.id == stateFlow.value.chatId } }
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnChatChanged(it.title)) }
            .launchIn(viewModelScope)
    }

    val chatMessages = stateFlow
        .map { it.chatId }
        .filterNotNull()
        .flatMapLatest { historyController.chatMessagePager(it).flow }
        .mapLatest { page -> page.map { ChatItem.Message(it) } }
        .mapLatest { page ->
            page.insertSeparators { before: ChatItem.Message?, after: ChatItem.Message? ->
                val beforeDate = before?.message?.dateMillis?.toInstantFromMillis()?.formatRelatively()
                val afterDate = after?.message?.dateMillis?.toInstantFromMillis()?.formatRelatively()

                if (afterDate == null) {
                    null
                } else if (before == null) {
                    ChatItem.Date(afterDate)
                } else if (beforeDate != afterDate) {
                    ChatItem.Date(afterDate)
                } else {
                    null
                }
            }
        }.cachedIn(viewModelScope)

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatIdChanged -> { state ->
                    state.copy(chatId = event.id)
                }
                is Event.OnChatChanged -> { state ->
                    state.copy(title = event.title)
                }
                Event.OnMuteToggled -> { state ->
                    state.copy(isMuted = !state.isMuted)
                }
            }
        }
    }
}