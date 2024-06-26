@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.map
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.model.Conversation
import com.getcode.model.ConversationMessageContent
import com.getcode.model.Feature
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.MessageContent
import com.getcode.model.TipChatCashFeature
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.Reference
import com.getcode.network.ConversationController
import com.getcode.network.repository.FeatureRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.util.CurrencyUtils
import com.getcode.util.formatted
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.toInstantFromMillis
import com.getcode.utils.ErrorUtils
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
        val reference: Reference.IntentId?,
        val title: String,
        val textFieldState: TextFieldState,
        val tipChatCash: Feature,
        val identityRevealed: Boolean,
        val user: User?,
        val lastSeen: Instant?
    ) {
        data class User(
            val username: String,
            val publicKey: PublicKey,
            val imageUrl: String?,
        )

        companion object {
            val Default = State(
                reference = null,
                tipChatCash = TipChatCashFeature(),
                title = "Anonymous Tipper",
                textFieldState = TextFieldState(),
                identityRevealed = false,
                user = null,
                lastSeen = null
            )
        }
    }

    sealed interface Event {
        data class OnChatIdChanged(val chatId: ID?): Event
        data class OnReferenceChanged(val reference: Reference.IntentId?) : Event
        data class OnConversationChanged(val conversation: Conversation) : Event
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

        data class Error(val message: String, val fatal: Boolean) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .mapNotNull {
                conversationController.getConversation(it)
            }.onEach {
                dispatchEvent(Event.OnConversationChanged(it))
            }.launchIn(viewModelScope)

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
                .map { it.conversation }
                .onEach { conversation ->
                    runCatching {
                        conversationController.openChatStream(viewModelScope, conversation)
                    }.onFailure {
                        it.printStackTrace()
                        ErrorUtils.handleError(it)
                    }
                }.flatMapLatest { conversationController.observeConversation(it.id) }
                .launchIn(viewModelScope)

        features.tipChatCash
            .onEach { dispatchEvent(Event.OnTipsChatCashChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendMessage>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString()
                Timber.d("sending message of $text")
                textFieldState.clearText()
                conversationController.sendMessage(it.reference?.id!!, text)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RevealIdentity>()
            .mapNotNull { stateFlow.value.reference?.id }
            .onEach { delay(300) }
            .onEach { conversationController.revealIdentity(it) }
            .launchIn(viewModelScope)
    }

    val messages: Flow<PagingData<ChatItem>> = stateFlow
        .map { it.reference?.id }
        .filterNotNull()
        .flatMapLatest { conversationController.conversationPagingData(it) }
        .map { page ->
            val state = stateFlow.value
            val username = state.user?.username.orEmpty()

            page.map { message ->
                val content = when (val contents = message.content) {
                    is ConversationMessageContent.IdentityRevealed -> {
                        MessageContent.Localized(
                            value = resources.getString(
                                resourceId = R.string.title_chat_announcement_identityRevealed,
                                username
                            ),
                        )
                    }

                    is ConversationMessageContent.IdentityRevealedToYou -> {
                        MessageContent.Localized(
                            value = resources.getString(
                                resourceId = R.string.title_chat_announcement_identityRevealedToYou,
                                username
                            ),
                        )
                    }

                    is ConversationMessageContent.Text -> {
                        MessageContent.Localized(
                            value = contents.message,
                            isFromSelf = contents.isFromSelf
                        )
                    }

                    is ConversationMessageContent.ThanksReceived -> {
                        MessageContent.ThankYou(
                            tipIntentId = emptyList(), // TODO:
                            isFromSelf = contents.isFromSelf
                        )
                    }

                    is ConversationMessageContent.ThanksSent -> {
                        MessageContent.Localized(
                            value = resources.getString(
                                resourceId = R.string.title_chat_announcement_thanksSent,
                            ),
                        )
                    }

                    is ConversationMessageContent.TipMessage -> {
                        MessageContent.Localized(
                            value = resources.getString(
                                resourceId = R.string.title_chat_announcement_tipHeader,
                                contents.kinAmount
                            ),
                        )
                    }
                }

                ChatItem.Message(
                    id = message.idBase58,
                    chatMessageId = stateFlow.value.reference?.id!!,
                    message = content,
                    date = message.dateMillis.toInstantFromMillis(),
                    status = message.status
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
                    state.copy(
                        title = event.conversation.title,
                        identityRevealed = event.conversation.hasRevealedIdentity
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

                is Event.OnChatIdChanged,
                is Event.Error,
                Event.RevealIdentity,
                Event.SendCash,
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