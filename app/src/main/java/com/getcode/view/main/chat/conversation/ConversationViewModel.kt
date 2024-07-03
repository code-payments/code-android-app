@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.flatMap
import androidx.paging.map
import com.getcode.BuildConfig
import com.getcode.model.ConversationWithLastPointers
import com.getcode.model.Feature
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.TipChatCashFeature
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.Reference
import com.getcode.model.uuid
import com.getcode.network.ConversationController
import com.getcode.network.repository.FeatureRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.ConversationMessageIndice
import com.getcode.util.CurrencyUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.toInstantFromMillis
import com.getcode.utils.ErrorUtils
import com.getcode.utils.floored
import com.getcode.utils.timestamp
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationController: ConversationController,
    currencyUtils: CurrencyUtils,
    resources: ResourceHelper,
    features: FeatureRepository,
) : BaseViewModel2<ConversationViewModel.State, ConversationViewModel.Event>(
    initialState = State.Default,
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val conversationId: ID?,
        val reference: Reference.IntentId?,
        val title: String,
        val textFieldState: TextFieldState,
        val tipChatCash: Feature,
        val identityRevealed: Boolean,
        val user: User?,
        val lastSeen: Instant?,
        val pointers: Map<UUID, MessageStatus>,
    ) {
        data class User(
            val username: String,
            val publicKey: PublicKey,
            val imageUrl: String?,
        )

        companion object {
            val Default = State(
                conversationId = null,
                reference = null,
                tipChatCash = TipChatCashFeature(),
                title = "Anonymous Tipper",
                textFieldState = TextFieldState(),
                identityRevealed = false,
                user = null,
                lastSeen = null,
                pointers = emptyMap(),
            )
        }
    }

    sealed interface Event {
        data class OnChatIdChanged(val chatId: ID?) : Event
        data class OnReferenceChanged(val reference: Reference.IntentId?) : Event
        data class OnConversationChanged(val conversationWithPointers: ConversationWithLastPointers) :
            Event

        data class OnUserRevealed(
            val username: String,
            val publicKey: PublicKey,
            val imageUrl: String?,
        ) : Event

        data class OnTipsChatCashChanged(val module: Feature) : Event

        data class OnUserActivity(val activity: Instant) : Event
        data class OnTitleChanged(val title: String) : Event
        data object SendCash : Event
        data object SendMessage : Event
        data object RevealIdentity : Event

        data object OnIdentityRevealed : Event

        data class OnPointersUpdated(val pointers: Map<UUID, MessageStatus>) : Event
        data class MarkRead(val messageId: ID) : Event
        data class MarkDelivered(val messageId: ID) : Event

        data class Error(val message: String, val fatal: Boolean) : Event
    }

    init {
        // this is an existing conversation so we fetch the chat directly
        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .mapNotNull {
                conversationController.getConversation(it)
            }.onEach {
                dispatchEvent(Event.OnConversationChanged(it))
            }.launchIn(viewModelScope)

        // reference ID is used to create a chat that is non-existent if needed
        eventFlow
            .filterIsInstance<Event.OnReferenceChanged>()
            .map { it.reference }
            .filterNotNull()
            .filterIsInstance<Reference.IntentId>()
            .map { it.id }
            .distinctUntilChanged()
            .mapNotNull { referenceId ->
                runCatching {
                    conversationController.getOrCreateConversation(referenceId, ChatType.TwoWay)
                }.onFailure {
                    it.printStackTrace()
                    dispatchEvent(
                        Event.Error(
                            message = if (BuildConfig.DEBUG) it.message.orEmpty() else "Failed to create conversation",
                            fatal = true
                        )
                    )
                }.getOrNull()
            }
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnConversationChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnConversationChanged>()
            .map { it.conversationWithPointers }
            .onEach { (conversation, pointer) ->
                runCatching {
                    conversationController.openChatStream(viewModelScope, conversation)
                }.onFailure {
                    it.printStackTrace()
                    ErrorUtils.handleError(it)
                }
            }.flatMapLatest { (conversation, _) ->
                conversationController.observeConversation(conversation.id)
            }.filterNotNull()
            .map { it.pointers }
            .distinctUntilChanged()
            .onEach {
                dispatchEvent(Event.OnPointersUpdated(it))
            }
            .launchIn(viewModelScope)

        features.tipChatCash
            .onEach { dispatchEvent(Event.OnTipsChatCashChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MarkRead>()
            .map { it.messageId }
            .filter { stateFlow.value.conversationId != null }
            .map { it to stateFlow.value.conversationId!! }
            .onEach { (messageId, conversationId) ->
                conversationController.advanceReadPointer(
                    conversationId,
                    messageId,
                    MessageStatus.Read
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MarkDelivered>()
            .map { it.messageId }
            .filter { stateFlow.value.conversationId != null }
            .map { it to stateFlow.value.conversationId!! }
            .onEach { (messageId, conversationId) ->
                conversationController.advanceReadPointer(
                    conversationId,
                    messageId,
                    MessageStatus.Delivered
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendMessage>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString()
                textFieldState.clearText()
                conversationController.sendMessage(it.conversationId!!, text)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RevealIdentity>()
            .mapNotNull { stateFlow.value.reference?.id }
            .onEach { delay(300) }
            .onEach { conversationController.revealIdentity(it) }
            .launchIn(viewModelScope)
    }

    val messages: Flow<PagingData<ChatItem>> = stateFlow
        .map { it.conversationId }
        .filterNotNull()
        .flatMapLatest { conversationController.conversationPagingData(it) }
        .map { page ->
            page.flatMap { mwc ->
                mwc.contents.map { ConversationMessageIndice(mwc.message, it) }
            }
        }
        .map { page ->
            page.map { indice ->
                val (message, contents) = indice

                val pointers = stateFlow.value.pointers
                val pointerRefs = pointers
                    .mapKeys { it.key.timestamp }
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }

                val messageTimestamp = message.id.uuid?.timestamp

                val status = findClosestMessageStatus(
                    timestamp = messageTimestamp,
                    statusMap = pointerRefs,
                    fallback = if (contents.isFromSelf) MessageStatus.Sent else MessageStatus.Unknown
                )

                ChatItem.Message(
                    chatMessageId = message.id,
                    message = contents,
                    date = message.dateMillis.toInstantFromMillis(),
                    status = status,
                    isFromSelf = contents.isFromSelf,
                    key = contents.hashCode() + message.id.hashCode()
                )
            }
        }

    override fun onCleared() {
        super.onCleared()
        conversationController.closeChatStream()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            Timber.d("event=${event}")
            when (event) {
                is Event.OnConversationChanged -> { state ->
                    val (conversation, _) = event.conversationWithPointers
                    state.copy(
                        conversationId = conversation.id,
                        title = conversation.title,
                        identityRevealed = conversation.hasRevealedIdentity,
                        pointers = event.conversationWithPointers.pointers
                    )
                }

                is Event.OnTipsChatCashChanged -> { state ->
                    state.copy(
                        tipChatCash = event.module
                    )
                }

                is Event.OnTitleChanged -> { state ->
                    state.copy(
                        title = event.title
                    )
                }

                is Event.OnPointersUpdated -> { state ->
                    state.copy(pointers = event.pointers)
                }

                is Event.OnChatIdChanged,
                is Event.Error,
                Event.RevealIdentity,
                Event.SendCash,
                is Event.MarkRead,
                is Event.MarkDelivered,
                is Event.SendMessage -> { state -> state }

                is Event.OnReferenceChanged -> { state ->
                    state.copy(reference = event.reference)
                }

                is Event.OnIdentityRevealed -> { state ->
                    state.copy(identityRevealed = true)
                }

                is Event.OnUserRevealed -> { state ->
                    state.copy(
                        user = State.User(
                            username = event.username,
                            publicKey = event.publicKey,
                            imageUrl = event.imageUrl,
                        )
                    )
                }

                is Event.OnUserActivity -> { state ->
                    state.copy(lastSeen = event.activity)
                }
            }
        }
    }
}

private fun findClosestMessageStatus(
    timestamp: Long?,
    statusMap: Map<Long, MessageStatus>,
    fallback: MessageStatus
): MessageStatus {
    timestamp ?: return fallback
    var closestKey: Long? = null

    for (key in statusMap.keys) {
        if (timestamp <= key && (closestKey == null || key <= closestKey)) {
            closestKey = key
        }
    }

    return closestKey?.let { statusMap[it] } ?: fallback
}