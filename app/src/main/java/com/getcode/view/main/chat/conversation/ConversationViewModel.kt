@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.flatMap
import androidx.paging.map
import com.codeinc.gen.user.v1.user
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.SessionController
import com.getcode.SessionEvent
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ConversationWithLastPointers
import com.getcode.model.Feature
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.ConversationCashFeature
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.KinAmount
import com.getcode.model.SocialUser
import com.getcode.model.TwitterUser
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.Platform
import com.getcode.model.chat.Reference
import com.getcode.model.uuid
import com.getcode.network.ConversationController
import com.getcode.network.TipController
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.FeatureRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.ConversationMessageIndice
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.toInstantFromMillis
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.bytes
import com.getcode.utils.catchSafely
import com.getcode.utils.timestamp
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.cos

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationController: ConversationController,
    features: FeatureRepository,
    tipController: TipController,
    exchange: Exchange,
    resources: ResourceHelper,
    sessionController: SessionController,
) : BaseViewModel2<ConversationViewModel.State, ConversationViewModel.Event>(
    initialState = State.Default,
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val conversationId: ID?,
        val twitterUser: TwitterUser?,
        val costToChat: KinAmount,
        val reference: Reference.IntentId?,
        val textFieldState: TextFieldState,
        val tipChatCash: Feature,
        val identityAvailable: Boolean,
        val identityRevealed: Boolean?,
        val users: List<User>,
        val lastSeen: Instant?,
        val pointers: Map<UUID, MessageStatus>,
    ) {
        data class User(
            val memberId: UUID,
            val username: String?,
            val imageUrl: String?,
        ) {
            val isRevealed: Boolean
                get() = username != null
        }

        companion object {
            val Default = State(
                twitterUser = null,
                costToChat = KinAmount.Zero,
                conversationId = null,
                reference = null,
                tipChatCash = ConversationCashFeature(),
                textFieldState = TextFieldState(),
                identityAvailable = false,
                identityRevealed = null,
                users = emptyList(),
                lastSeen = null,
                pointers = emptyMap(),
            )
        }
    }

    sealed interface Event {
        data class OnTwitterUserChanged(val user: TwitterUser?) : Event
        data class OnCostToChatChanged(val cost: KinAmount) : Event
        data class OnMembersChanged(val members: List<State.User>) : Event
        data class OnChatIdChanged(val chatId: ID?) : Event
        data class OnConversationChanged(val conversationWithPointers: ConversationWithLastPointers) :
            Event

        data class OnUserRevealed(
            val memberId: UUID,
            val username: String? = null,
            val imageUrl: String? = null,
        ) : Event

        data class OnTipsChatCashChanged(val module: Feature) : Event

        data class OnUserActivity(val activity: Instant) : Event
        data object SendCash : Event
        data object SendMessage : Event
        data object RevealIdentity : Event

        data class OnIdentityAvailable(val available: Boolean) : Event
        data object OnIdentityRevealed : Event

        data class OnPointersUpdated(val pointers: Map<UUID, MessageStatus>) : Event
        data class MarkRead(val messageId: ID) : Event
        data class MarkDelivered(val messageId: ID) : Event

        data object PresentPaymentConfirmation : Event

        data class Error(val fatal: Boolean, val message: String = "", val show: Boolean = true) :
            Event
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

        eventFlow
            .filterIsInstance<Event.OnTwitterUserChanged>()
            .map { it.user }
            .filterNotNull()
            .mapNotNull { user ->
                val currencySymbol = user.costOfFriendship.currency
                val rate = exchange.rateFor(currencySymbol) ?: exchange.rateForUsd()!!

                user to KinAmount.fromFiatAmount(fiat = user.costOfFriendship, rate = rate)
            }.map { (user, cost) ->
                dispatchEvent(Event.OnCostToChatChanged(cost))
                user
            }.onEach { user ->
                val member = user.let {
                    State.User(
                        memberId = UUID.randomUUID(),
                        username = user.username,
                        imageUrl = user.imageUrl
                    )
                }

                dispatchEvent(Event.OnMembersChanged(listOf(member)))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentPaymentConfirmation>()
            .mapNotNull {
                val state = stateFlow.value
                if (state.twitterUser == null) return@mapNotNull null
                state.twitterUser to state.costToChat
            }.onEach { (user, amount) ->
                sessionController.presentPrivatePaymentConfirmation(
                    socialUser = user,
                    amount = amount
                )
            }.launchIn(viewModelScope)

        sessionController.eventFlow
            .filterIsInstance<SessionEvent.OnChatPaidForSuccessfully>()
            .onEach { event ->
                runCatching {
                    val conversation = conversationController.getOrCreateConversation(
                        identifier = event.intentId,
                        with = event.user
                    )
                    dispatchEvent(Event.OnConversationChanged(conversation))
                }.onFailure {
                    it.printStackTrace()
                    TopBarManager.showMessage(
                        "Failed to Start Chat",
                        "We were unable to start a chat with ${event.user.username}. Please try again.",
                    )

                    dispatchEvent(
                        Event.Error(
                            message = if (BuildConfig.DEBUG) it.message.orEmpty() else "Failed to create conversation",
                            show = false,
                            fatal = true
                        )
                    )
                }.getOrNull()
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnConversationChanged>()
            .map { it.conversationWithPointers }
            .distinctUntilChangedBy { it.conversation.id }
            .onEach { conversationController.resetUnreadCount(it.conversation.id) }
            .onEach { (conversation, _) ->
                runCatching {
                    conversationController.openChatStream(viewModelScope, conversation)
                }.onFailure {
                    it.printStackTrace()
                    ErrorUtils.handleError(it)
                }
            }.flatMapLatest { (conversation, _) ->
                conversationController.observeConversation(conversation.id)
            }.filterNotNull()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnConversationChanged(it)) }
            .launchIn(viewModelScope)

        features.conversationsCash
            .onEach { dispatchEvent(Event.OnTipsChatCashChanged(it)) }
            .launchIn(viewModelScope)

        tipController.connectedAccount
            .onEach {
                dispatchEvent(Event.OnIdentityAvailable(it != null))
            }.launchIn(viewModelScope)

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
                    .onSuccess {
                        trace(
                            tag = "Conversation",
                            message = "message sent successfully",
                            type = TraceType.Silent
                        )
                    }
                    .onFailure { error ->
                        trace(
                            tag = "Conversation",
                            message = "message failed to send",
                            type = TraceType.Error,
                            error = error
                        )
                    }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RevealIdentity>()
            .mapNotNull { stateFlow.value.conversationId }
            .onEach { conversationId ->
                val user = stateFlow.value.users.firstOrNull()?.username ?: "This user"
                val identity = tipController.connectedAccount.value ?: return@onEach
                val platform = when (identity) {
                    is TwitterUser -> Platform.Twitter
                }
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_revealIdentity),
                        subtitle = resources.getString(
                            R.string.prompt_subtitle_revealIdentity,
                            user,
                            identity.username
                        ),
                        positiveText = resources.getString(R.string.action_yes),
                        type = BottomBarManager.BottomBarMessageType.REMOTE_SEND,
                        onPositive = {
                            viewModelScope.launch {
                                conversationController.revealIdentity(
                                    conversationId,
                                    platform,
                                    identity.username
                                ).onSuccess { dispatchEvent(Event.OnIdentityRevealed) }
                                    .onFailure { it.printStackTrace() }
                            }
                        },
                        negativeText = resources.getString(R.string.action_nevermind)
                    )
                )
            }
            .launchIn(viewModelScope)

        stateFlow
            .mapNotNull { it.users }
            .distinctUntilChanged()
            .flatMapLatest { users ->
                users.asFlow() // Convert the list to a flow
            }
            .filter { it.username != null }
            .mapNotNull { user ->
                val username = user.username ?: return@mapNotNull null
                runCatching { tipController.fetch(username) }
                    .getOrNull() to user
            }
            .onEach { (result, user) ->
                if (result != null) {
                    dispatchEvent(
                        Event.OnUserRevealed(
                            memberId = user.memberId,
                            result.username,
                            result.imageUrl
                        )
                    )
                }
            }
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
                    val members = conversation.nonSelfMembers

                    state.copy(
                        conversationId = conversation.id,
                        identityRevealed = conversation.hasRevealedIdentity,
                        pointers = event.conversationWithPointers.pointers,
                        twitterUser = null,
                        users = members.map {
                            State.User(
                                memberId = it.id,
                                username = it.identity?.username,
                                imageUrl = null,
                            )
                        }
                    )
                }

                is Event.OnTipsChatCashChanged -> { state ->
                    state.copy(
                        tipChatCash = event.module
                    )
                }

                is Event.OnCostToChatChanged -> { state ->
                    state.copy(costToChat = event.cost)
                }

                is Event.OnMembersChanged -> { state ->
                    state.copy(users = event.members)
                }

                is Event.OnPointersUpdated -> { state ->
                    state.copy(pointers = event.pointers)
                }

                is Event.OnIdentityAvailable -> { state ->
                    state.copy(identityAvailable = event.available)
                }

                is Event.OnTwitterUserChanged -> { state ->
                    state.copy(twitterUser = event.user)
                }

                is Event.PresentPaymentConfirmation,
                is Event.OnChatIdChanged,
                is Event.Error,
                Event.RevealIdentity,
                Event.SendCash,
                is Event.MarkRead,
                is Event.MarkDelivered,
                is Event.SendMessage -> { state -> state }

                is Event.OnIdentityRevealed -> { state ->
                    state.copy(identityRevealed = true)
                }

                is Event.OnUserRevealed -> { state ->
                    val users = state.users
                    val updatedUsers = users.map {
                        if (it.memberId == event.memberId) {
                            it.copy(
                                username = event.username ?: it.username,
                                imageUrl = event.imageUrl ?: it.imageUrl,
                            )
                        } else {
                            it
                        }
                    }

                    state.copy(users = updatedUsers)
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