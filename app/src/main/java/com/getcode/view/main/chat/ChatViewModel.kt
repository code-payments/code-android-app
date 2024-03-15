package com.getcode.view.main.chat

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import androidx.paging.map
import com.getcode.model.Chat
import com.getcode.model.ID
import com.getcode.model.MessageContent
import com.getcode.model.Title
import com.getcode.network.HistoryController
import com.getcode.util.formatDateRelatively
import com.getcode.util.toInstantFromMillis
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
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
import java.util.UUID
import javax.inject.Inject

typealias ChatMessageIndice = Triple<MessageContent, ID, Instant>

sealed class ChatItem(val key: Any) {
    data class Message(
        val id: String = UUID.randomUUID().toString(),
        val chatMessageId: ID,
        val message: MessageContent,
        val date: Instant
    ) : ChatItem(id)

    data class Date(val date: String) : ChatItem(date)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    historyController: HistoryController,
) : BaseViewModel2<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(
        chatId = null,
        title = null,
        canMute = false,
        isMuted = false,
        canUnsubscribe = false,
        isSubscribed = false
    ),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val chatId: ID?,
        val title: Title?,
        val canMute: Boolean,
        val isMuted: Boolean,
        val canUnsubscribe: Boolean,
        val isSubscribed: Boolean,
    )

    sealed interface Event {
        data class OnChatIdChanged(val id: ID?) : Event
        data class OnChatChanged(val chat: Chat) : Event
        data object OnMuteToggled : Event
        data object OnSubscribeToggled : Event
        data class SetMuted(val muted: Boolean) : Event
        data class SetSubscribed(val subscribed: Boolean) : Event
    }

    init {
        stateFlow
            .map { it.chatId }
            .filterNotNull()
            .onEach { historyController.advanceReadPointer(it) }
            .flatMapLatest { historyController.chats }
            .flowOn(Dispatchers.IO)
            .filterNotNull()
            .mapNotNull { chats -> chats.firstOrNull { it.id == stateFlow.value.chatId } }
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnChatChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMuteToggled>()
            .map { stateFlow.value.chatId to stateFlow.value.isMuted }
            .filter { it.first != null }
            .map { it.first!! to it.second }
            .map { (chatId, muted) ->
                dispatchEvent(Event.SetMuted(!muted))
                historyController.setMuted(chatId, !muted)
            }
            .onEach { result ->
                if (result.isSuccess) {
                    val muted = result.getOrNull() ?: false
                    Timber.d(if (muted) "Muted chat" else "Unmuted chat")
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                    dispatchEvent(Event.SetMuted(!stateFlow.value.isMuted))
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSubscribeToggled>()
            .map { stateFlow.value.chatId to stateFlow.value.isSubscribed }
            .filter { it.first != null }
            .map { it.first!! to it.second }
            .map { (chatId, subscribed) ->
                dispatchEvent(Event.SetSubscribed(!subscribed))
                historyController.setSubscribed(chatId, !subscribed)
            }
            .onEach { result ->
                if (result.isSuccess) {
                    val subbed = result.getOrNull() ?: false
                    Timber.d(if (subbed) "Subscribed" else "Unsubscribe")
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                    dispatchEvent(Event.SetSubscribed(!stateFlow.value.isSubscribed))
                }
            }
            .launchIn(viewModelScope)
    }

    val chatMessages = stateFlow
        .map { it.chatId }
        .filterNotNull()
        .flatMapLatest { historyController.chatFlow(it) }
        .mapLatest { page ->
            page.flatMap { chat ->
                chat.contents
                    .sortedWith(compareBy { it is MessageContent.Localized })
                    .map { ChatMessageIndice(it, chat.id, chat.dateMillis.toInstantFromMillis()) }
            }
        }
        .mapLatest { page ->
            page.map { (message, id, date) ->
                ChatItem.Message(
                    chatMessageId = id,
                    message = message,
                    date = date
                )
            }
        }
        .mapLatest { page ->
            page.insertSeparators { before: ChatItem.Message?, after: ChatItem.Message? ->
                val beforeDate = before?.date?.formatDateRelatively()
                val afterDate = after?.date?.formatDateRelatively()

                if (beforeDate != afterDate) {
                    beforeDate?.let { ChatItem.Date(it) }
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
                    state.copy(
                        title = event.chat.title,
                        canMute = event.chat.canMute,
                        isMuted = event.chat.isMuted,
                        canUnsubscribe = event.chat.canUnsubscribe,
                    )
                }

                Event.OnMuteToggled,
                Event.OnSubscribeToggled -> { state -> state }

                is Event.SetMuted -> { state ->
                    state.copy(isMuted = event.muted)
                }
                is Event.SetSubscribed -> { state ->
                    state.copy(isSubscribed = event.subscribed)
                }
            }
        }
    }
}