package com.getcode.view.main.chat

import androidx.lifecycle.viewModelScope
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import androidx.paging.map
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.NotificationCollectionEntity
import com.getcode.model.chat.Reference
import com.getcode.model.chat.Sender
import com.getcode.model.chat.Title
import com.getcode.model.chat.Verb
import com.getcode.network.NotificationCollectionHistoryController
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.ChatMessageIndice
import com.getcode.util.formatDateRelatively
import com.getcode.util.toInstantFromMillis
import com.getcode.utils.base58
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
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
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationCollectionViewModel @Inject constructor(
    historyController: NotificationCollectionHistoryController,
    betaFlags: BetaFlagsRepository,
) : BaseViewModel2<NotificationCollectionViewModel.State, NotificationCollectionViewModel.Event>(
    initialState = State(
        chatId = null,
        chat = null,
        title = null,
        canMute = false,
        isMuted = false,
        _canUnsubscribe = false,
        unsubscribeEnabled = false,
        isSubscribed = false,
    ),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val chatId: ID?,
        val chat: NotificationCollectionEntity?,
        val title: Title?,
        val canMute: Boolean,
        val isMuted: Boolean,
        private val _canUnsubscribe: Boolean,
        private val unsubscribeEnabled: Boolean,
        val isSubscribed: Boolean,
    ) {
        val canUnsubscribe: Boolean
            get() = _canUnsubscribe && unsubscribeEnabled
    }

    sealed interface Event {
        data class OnChatIdChanged(val id: ID?) : Event
        data class OnChatChanged(val chat: NotificationCollectionEntity) : Event
        data object OnMuteToggled : Event
        data object OnSubscribeToggled : Event
        data class SetMuted(val muted: Boolean) : Event
        data class SetSubscribed(val subscribed: Boolean) : Event
        data class EnableUnsubscribe(val enabled: Boolean) : Event
        data class OpenMessageChat(val reference: Reference) : Event
    }

    init {
        stateFlow
            .map { it.chatId }
            .onEach { Timber.d("chatid=${it?.base58}") }
            .filterNotNull()
            .onEach { historyController.advanceReadPointer(it) }
            .flatMapLatest { historyController.notifications }
            .flowOn(Dispatchers.IO)
            .filterNotNull()
            .mapNotNull { chats -> chats.firstOrNull { it.id == stateFlow.value.chatId } }
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnChatChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMuteToggled>()
            .map { stateFlow.value.chat to stateFlow.value.isMuted }
            .filter { it.first != null }
            .map { it.first!! to it.second }
            .map { (chat, muted) ->
                dispatchEvent(Event.SetMuted(!muted))
                historyController.setMuted(chat, !muted)
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
            .map { stateFlow.value.chat to stateFlow.value.isSubscribed }
            .filter { it.first != null }
            .map { it.first!! to it.second }
            .map { (chat, subscribed) ->
                dispatchEvent(Event.SetSubscribed(!subscribed))
                historyController.setSubscribed(chat, !subscribed)
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

        betaFlags.observe()
            .map { it.chatUnsubEnabled }
            .distinctUntilChanged()
            .onEach {
                dispatchEvent(Event.EnableUnsubscribe(it))
            }.launchIn(viewModelScope)
    }

    val chatMessages = stateFlow
        .map { it.chatId }
        .filterNotNull()
        .flatMapLatest { historyController.collectionFlow(it) }
        .mapLatest { page ->
            page.flatMap { message ->
                message.contents
                    .sortedWith(compareBy { it is MessageContent.Localized })
                    .map {
                        ChatMessageIndice(
                            message,
                            it,
                        )
                    }
            }
        }
        .mapLatest { page ->
            page.map { (message, contents) ->
                ChatItem.Message(
                    chatMessageId = message.id,
                    message = contents,
                    date = message.dateMillis.toInstantFromMillis(),
                    status = if (contents.isFromSelf) MessageStatus.Sent else MessageStatus.Unknown,
                    sender = Sender(
                        id = null,
                        name = null,
                        profileImageUrl = null,
                        isHost = false,
                        isSelf = contents.isFromSelf,

                    )
                )
            }
        }
        .mapLatest { page ->
            page.insertSeparators { before: ChatItem.Message?, after: ChatItem.Message? ->
                if (before?.date != after?.date) {
                    before?.date?.let { ChatItem.Date(it) }
                } else {
                    null
                }
            }
        }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatIdChanged -> { state ->
                    state.copy(chatId = event.id)
                }

                is Event.OnChatChanged -> { state ->
                    state.copy(
                        chat = event.chat,
                        title = event.chat.title,
                        canMute = event.chat.canMute,
                        isMuted = event.chat.isMuted,
                        isSubscribed = event.chat.isSubscribed,
                        _canUnsubscribe = event.chat.canUnsubscribe,
                    )
                }

                is Event.OpenMessageChat,
                Event.OnMuteToggled,
                Event.OnSubscribeToggled -> { state -> state }

                is Event.SetMuted -> { state ->
                    state.copy(isMuted = event.muted)
                }

                is Event.SetSubscribed -> { state ->
                    state.copy(isSubscribed = event.subscribed)
                }

                is Event.EnableUnsubscribe -> { state ->
                    state.copy(unsubscribeEnabled = event.enabled)
                }
            }
        }
    }
}