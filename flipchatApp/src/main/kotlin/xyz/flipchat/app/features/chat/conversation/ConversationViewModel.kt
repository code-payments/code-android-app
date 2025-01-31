package xyz.flipchat.app.features.chat.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.chat.Deleter
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.model.uuid
import com.getcode.navigation.RoomInfoArgs
import com.getcode.services.model.ExtendedMetadata
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import com.getcode.ui.components.chat.messagecontents.MessageControls
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.chat.utils.ReplyMessageAnchor
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.toInstantFromMillis
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.network.retryable
import com.getcode.utils.timestamp
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import timber.log.Timber
import xyz.flipchat.app.R
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.features.login.register.onError
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.app.util.IntentUtils
import xyz.flipchat.chat.RoomController
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.metadata.JoinChatPaymentMetadata
import xyz.flipchat.services.data.metadata.SendMessageAsListenerPaymentMetadata
import xyz.flipchat.services.data.metadata.SendTipMessagePaymentMetadata
import xyz.flipchat.services.data.metadata.erased
import xyz.flipchat.services.data.metadata.typeUrl
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers
import xyz.flipchat.services.extensions.titleOrFallback
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val userManager: UserManager,
    private val roomController: RoomController,
    private val chatsController: ChatsController,
    private val paymentController: PaymentController,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
    private val currencyUtils: CurrencyUtils,
    clipboardManager: ClipboardManager,
    private val betaFeatures: Labs,
) : BaseViewModel2<ConversationViewModel.State, ConversationViewModel.Event>(
    initialState = State.Default,
    updateStateForEvent = updateStateForEvent
) {

    private var typingJob: Job? = null

    data class State(
        val selfId: ID?,
        val selfName: String?,
        val hostId: ID?,
        val conversationId: ID?,
        val unreadCount: Int?,
        val chattableState: ChattableState?,
        val textFieldState: TextFieldState,
        val replyEnabled: Boolean,
        val replyMessage: MessageReplyAnchor?,
        val startAtUnread: Boolean,
        val unreadStateHandled: Boolean,
        val title: String,
        val imageUri: String?,
        val lastSeen: Instant?,
        val members: Int?,
        val pointers: Map<UUID, MessageStatus?>,
        val pointerRefs: Map<Long, MessageStatus>,
        val showTypingIndicator: Boolean,
        val isSelfTyping: Boolean,
        val isRoomOpen: Boolean,
        val isOpenCloseEnabled: Boolean,
        val isTippingEnabled: Boolean,
        val isLinkImagePreviewsEnabled: Boolean,
        val roomInfoArgs: RoomInfoArgs,
        val lastReadMessage: UUID?,
    ) {
        val isHost: Boolean
            get() = selfId != null && hostId != null && selfId == hostId

        companion object {
            val Default = State(
                selfId = null,
                selfName = null,
                hostId = null,
                imageUri = null,
                conversationId = null,
                unreadCount = null,
                unreadStateHandled = false,
                chattableState = null,
                lastReadMessage = null,
                textFieldState = TextFieldState(),
                replyEnabled = false,
                startAtUnread = false,
                replyMessage = null,
                title = "",
                lastSeen = null,
                pointers = emptyMap(),
                pointerRefs = emptyMap(),
                members = null,
                showTypingIndicator = false,
                isSelfTyping = false,
                isRoomOpen = true,
                isOpenCloseEnabled = false,
                isTippingEnabled = false,
                isLinkImagePreviewsEnabled = false,
                roomInfoArgs = RoomInfoArgs(),
            )
        }
    }

    sealed interface Event {
        data class OnSelfChanged(val id: ID?, val displayName: String?) : Event
        data class OnChatIdChanged(val chatId: ID?) : Event
        data class OnRoomNumberChanged(val roomNumber: Long) : Event
        data class OnConversationChanged(val conversationWithPointers: ConversationWithMembersAndLastPointers) :
            Event

        data class OnInitialUnreadCountDetermined(val count: Int) : Event
        data object OnUnreadStateHandled : Event
        data class OnTitlesChanged(val title: String, val roomCardTitle: String) : Event
        data class OnUserActivity(val activity: Instant) : Event
        data object OnSendCash : Event
        data object OnSendMessage : Event
        data class SendMessage(val paymentId: ID? = null) : Event
        data object SendMessageWithFee : Event
        data object RevealIdentity : Event

        data class OnAbilityToChatChanged(val state: ChattableState) : Event
        data class OnPointersUpdated(val pointers: Map<UUID, MessageStatus>) : Event
        data class MarkRead(val messageId: ID) : Event
        data class MarkDelivered(val messageId: ID) : Event

        data class OnReplyEnabled(val enabled: Boolean) : Event
        data class OnOpenCloseEnabled(val enabled: Boolean) : Event
        data class OnTippingEnabled(val enabled: Boolean) : Event
        data class OnLinkImagePreviewsEnabled(val enabled: Boolean) : Event
        data class ReplyTo(val anchor: MessageReplyAnchor) : Event {
            constructor(chatItem: ChatItem.Message) : this(
                MessageReplyAnchor(
                    chatItem.chatMessageId,
                    chatItem.sender,
                    chatItem.message
                )
            )
        }

        data object CancelReply : Event

        data class OnStartAtUnread(val enabled: Boolean) : Event

        data object OnJoinRequestedFromSpectating : Event
        data object OnSendMessageForFee : Event
        data object NeedsAccountCreated : Event
        data object OnAccountCreated : Event
        data object OnJoinRoom : Event

        data object Resumed : Event
        data object Stopped : Event

        data object OnTypingStarted : Event
        data object OnTypingStopped : Event

        data object OnOpenStateChangedRequested : Event
        data class OnOpenRoom(val conversationId: ID) : Event
        data class OnCloseRoom(val conversationId: ID) : Event

        data class CopyMessage(val text: String) : Event
        data class DeleteMessage(val conversationId: ID, val messageId: ID) : Event
        data class RemoveUser(val conversationId: ID, val userId: ID) : Event
        data class ReportUser(val userId: ID, val messageId: ID) : Event
        data class MuteUser(val conversationId: ID, val userId: ID) : Event
        data class BlockUser(val userId: ID) : Event
        data class UnblockUser(val userId: ID) : Event
        data class TipUser(val messageId: ID, val userId: ID) : Event
        data class PromoteUser(val conversationId: ID, val userId: ID) : Event
        data class DemoteUser(val conversationId: ID, val userId: ID) : Event

        data object OnShareRoomLink : Event
        data class ShareRoom(val intent: Intent) : Event

        data object OnUserTypingStarted : Event
        data object OnUserTypingStopped : Event

        data class LookupRoom(val number: Long) : Event
        data class OpenRoomPreview(val roomInfoArgs: RoomInfoArgs) : Event
        data class OpenRoom(val roomId: ID) : Event

        data class Error(
            val fatal: Boolean,
            val message: String = "",
            val show: Boolean = true
        ) : Event
    }

    init {
        userManager.state
            .map { it.userId to it.displayName }
            .distinctUntilChanged()
            .onEach { (id, displayName) ->
                dispatchEvent(Event.OnSelfChanged(id, displayName))
            }.launchIn(viewModelScope)

        betaFeatures.observe(Lab.ReplyToMessage)
            .onEach { dispatchEvent(Event.OnReplyEnabled(it)) }
            .launchIn(viewModelScope)

        betaFeatures.observe(Lab.OpenCloseRoom)
            .onEach { dispatchEvent(Event.OnOpenCloseEnabled(it)) }
            .launchIn(viewModelScope)

        betaFeatures.observe(Lab.StartChatAtUnread)
            .onEach { dispatchEvent(Event.OnStartAtUnread(it)) }
            .launchIn(viewModelScope)

        betaFeatures.observe(Lab.Tipping)
            .onEach { dispatchEvent(Event.OnTippingEnabled(it)) }
            .launchIn(viewModelScope)

        betaFeatures.observe(Lab.LinkImages)
            .onEach { dispatchEvent(Event.OnLinkImagePreviewsEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .map { roomController.getUnreadCount(it) }
            .onEach { dispatchEvent(Event.OnInitialUnreadCountDetermined(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .mapNotNull {
                retryable(
                    maxRetries = 5,
                    delayDuration = 3.seconds,
                ) { roomController.getConversation(it) }
            }.onEach { dispatchEvent(Event.OnConversationChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .onEach {
                runCatching {
                    roomController.openMessageStream(viewModelScope, it)
                }.onFailure {
                    it.printStackTrace()
                    ErrorUtils.handleError(it)
                }
            }.launchIn(viewModelScope)

        stateFlow
            .mapNotNull { it.conversationId }
            .distinctUntilChanged()
            .flatMapLatest {
                roomController.observeConversation(it)
            }.filterNotNull()
            .distinctUntilChanged()
            .onEach {
                val (conversation, members, _) = it
                val selfMember = members.firstOrNull { userManager.isSelf(it.id) }
                val chattableState = if (selfMember != null) {
                    val isMuted = selfMember.isMuted
                    val isSpectator = !selfMember.isFullMember
                    val isRoomClosedAsMember = !conversation.isOpen && !selfMember.isHost

                    when {
                        isRoomClosedAsMember -> ChattableState.DisabledByClosedRoom
                        isSpectator -> ChattableState.Spectator(
                            Kin.fromQuarks(it.conversation.messagingFee ?: 0)
                        )

                        isMuted -> ChattableState.DisabledByMute
                        else -> ChattableState.Enabled
                    }
                } else {
                    ChattableState.Enabled
                }

                if (stateFlow.value.chattableState != chattableState) {
                    dispatchEvent(Event.OnAbilityToChatChanged(chattableState))
                }

                dispatchEvent(Event.OnConversationChanged(it))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnConversationChanged>()
            .map { it.conversationWithPointers.conversation }
            .distinctUntilChanged()
            .map {
                val title = it.titleOrFallback(resources, includePrefix = true)
                val roomCardTitle = it.titleOrFallback(resources, includePrefix = false)
                title to roomCardTitle
            }.distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnTitlesChanged(it.first, it.second)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MarkRead>()
            .onEach { delay(300) }
            .map { it.messageId }
            .filter { stateFlow.value.conversationId != null }
            .map { it to stateFlow.value.conversationId!! }
            .onEach { (messageId, conversationId) ->
                roomController.advancePointer(
                    conversationId,
                    messageId,
                    MessageStatus.Read
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MarkDelivered>()
            .onEach { delay(300) }
            .map { it.messageId }
            .filter { stateFlow.value.conversationId != null }
            .map { it to stateFlow.value.conversationId!! }
            .onEach { (messageId, conversationId) ->
                roomController.advancePointer(
                    conversationId,
                    messageId,
                    MessageStatus.Delivered
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSendMessage>()
            .onEach {
                if (stateFlow.value.chattableState is ChattableState.Enabled) {
                    dispatchEvent(Event.SendMessage())
                } else {
                    dispatchEvent(Event.SendMessageWithFee)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendMessageWithFee>()
            .map { stateFlow.value.roomInfoArgs }
            .filter { it.ownerId != null }
            .map { profileController.getPaymentDestinationForUser(it.ownerId!!) }
            .mapNotNull {
                if (it.isSuccess) {
                    val paymentDestination = it.getOrNull() ?: return@mapNotNull null
                    val sendMessageMetadata = SendMessageAsListenerPaymentMetadata(
                        userId = userManager.userId!!,
                        chatId = stateFlow.value.conversationId!!
                    )

                    val metadata = ExtendedMetadata.Any(
                        data = sendMessageMetadata.erased(),
                        typeUrl = sendMessageMetadata.typeUrl
                    )

                    val amount =
                        KinAmount.fromQuarks(stateFlow.value.roomInfoArgs.messagingFeeQuarks)

                    paymentController.presentPublicPaymentConfirmation(
                        amount = amount,
                        destination = paymentDestination,
                        metadata = metadata,
                    )
                } else {
                    return@mapNotNull null
                }
            }.flatMapLatest {
                paymentController.eventFlow.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit

                    is PaymentEvent.OnPaymentSuccess -> {
                        event.acknowledge(true) {
                            dispatchEvent(Event.SendMessage(event.intentId))
                        }
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendMessage>()
            .mapNotNull {
                val paymentId = it.paymentId
                val state = stateFlow.value
                val textFieldState = state.textFieldState
                val text = textFieldState.text.toString().trim()
                if (text.isEmpty()) return@mapNotNull null

                textFieldState.clearText()

                val replyingTo = state.replyMessage

                dispatchEvent(Event.CancelReply)

                if (replyingTo != null) {
                    roomController.sendReply(
                        state.conversationId!!,
                        replyingTo.id,
                        text
                    )
                } else {
                    roomController.sendMessage(state.conversationId!!, text, paymentId)
                }
            }.onResult(
                onError = {
                    trace(
                        tag = "Conversation",
                        message = "message failed to send",
                        type = TraceType.Error,
                        error = it
                    )
                },
                onSuccess = {
                    // if we are temporarily allowed to speak, reset back to spectator
                    if (stateFlow.value.chattableState is ChattableState.TemporarilyEnabled) {
                        val fee = Kin.fromQuarks(stateFlow.value.roomInfoArgs.messagingFeeQuarks)
                        dispatchEvent(Event.OnAbilityToChatChanged(ChattableState.Spectator(fee)))
                    }

                    trace(
                        tag = "Conversation",
                        message = "message sent successfully",
                        type = TraceType.Silent
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Resumed>()
            .mapNotNull { stateFlow.value.conversationId }
            .distinctUntilChanged()
            .onEach {
                runCatching {
                    roomController.openMessageStream(viewModelScope, it)
                }.onFailure {
                    ErrorUtils.handleError(it)
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccountCreated>()
            .onEach { delay(400) }
            .onEach { dispatchEvent(Event.OnJoinRoom) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Stopped>()
            .onEach {
                roomController.closeMessageStream()
                userManager.roomClosed()
            }.launchIn(viewModelScope)

        stateFlow
            .map { it.conversationId }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest {
                roomController.observeTyping(it)
            }
            .onEach { isOtherUserTyping ->
                if (isOtherUserTyping) {
                    dispatchEvent(Event.OnTypingStarted)
                } else {
                    dispatchEvent(Event.OnTypingStopped)
                }
            }.launchIn(viewModelScope)

        stateFlow
            .map { it.textFieldState }
            .flatMapLatest { ts -> snapshotFlow { ts.text } }
            .distinctUntilChanged()
            .onEach { text ->
                typingJob?.cancel()

                if (text.isEmpty()) {
                    dispatchEvent(Event.OnUserTypingStopped)
                } else {
                    if (!stateFlow.value.isSelfTyping) {
                        dispatchEvent(Event.OnUserTypingStarted)
                    }

                    typingJob = viewModelScope.launch {
                        delay(1.seconds)
                        dispatchEvent(Event.OnUserTypingStopped)
                    }
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnUserTypingStarted>()
            .mapNotNull { stateFlow.value.conversationId }
            .onEach { roomController.onUserStartedTypingIn(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnUserTypingStopped>()
            .mapNotNull { stateFlow.value.conversationId }
            .onEach { roomController.onUserStoppedTypingIn(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpenStateChangedRequested>()
            .mapNotNull { stateFlow.value.conversationId }
            .map { it to stateFlow.value.isRoomOpen }
            .onEach { (conversationId, isOpen) ->
                confirmOpenStateChange(conversationId, isOpen)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpenRoom>()
            .map { it.conversationId }
            .map { roomController.enableChat(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToReopenRoom),
                            resources.getString(R.string.error_description_failedToReopenRoom)
                        )
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCloseRoom>()
            .map { it.conversationId }
            .map { roomController.disableChat(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToCloseRoom),
                            resources.getString(R.string.error_description_failedToCloseRoom)
                        )
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DeleteMessage>()
            .map { (conversationId, messageId) ->
                roomController.deleteMessage(conversationId, messageId)
            }.onError {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        title = resources.getString(R.string.error_title_failedToDeleteMessage),
                        message = resources.getString(R.string.error_description_failedToDeleteMessage)
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RemoveUser>()
            .map { (conversationId, userId) ->
                roomController.removeUser(conversationId, userId)
            }.onError {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        title = resources.getString(R.string.error_title_failedToRemoveUser),
                        message = resources.getString(R.string.error_description_failedToRemoveUser)
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ReportUser>()
            .map { (userId, messageId) ->
                roomController.reportUserForMessage(userId, messageId)
            }.onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToReportUserForMessage),
                            message = resources.getString(R.string.error_description_failedToReportUserForMessage)
                        )
                    )
                },
                onSuccess = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.success_title_reportUser),
                            message = resources.getString(R.string.success_description_reportUser),
                            type = TopBarManager.TopBarMessageType.SUCCESS
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MuteUser>()
            .map { (chatId, userId) ->
                roomController.muteUser(chatId, userId)
            }.onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToMuteUser),
                            message = resources.getString(R.string.error_description_failedToMuteUser)
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.BlockUser>()
            .map { it.userId }
            .map { roomController.blockUser(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToBlockUser),
                            message = resources.getString(R.string.error_description_failedToBlockUser)
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnblockUser>()
            .map { it.userId }
            .map { roomController.unblockUser(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToUnblockUser),
                            message = resources.getString(R.string.error_description_failedToUnblockUser)
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyMessage>()
            .map { it.text }
            .onEach {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.TipUser>()
            .map { data ->
                val result = profileController.getPaymentDestinationForUser(data.userId)
                if (result.isSuccess) {
                    result.getOrNull()?.let { Result.success(it to data.messageId) }
                        ?: Result.failure(
                            Throwable()
                        )
                } else {
                    Result.failure(result.exceptionOrNull() ?: Throwable())
                }
            }
            .mapNotNull {
                if (it.isSuccess) {
                    val (paymentDestination, messageId) = it.getOrNull() ?: return@mapNotNull null
                    val tipPaymentMetadata = SendTipMessagePaymentMetadata(
                        tipperId = userManager.userId!!,
                        chatId = stateFlow.value.conversationId!!,
                        messageId = messageId
                    )

                    val metadata = ExtendedMetadata.Any(
                        data = tipPaymentMetadata.erased(),
                        typeUrl = tipPaymentMetadata.typeUrl
                    )

                    paymentController.presentMessageTipConfirmation(
                        destination = paymentDestination,
                        metadata = metadata
                    )
                } else {
                    return@mapNotNull null
                }
            }.flatMapLatest {
                paymentController.eventFlow.take(1)
            }
            .onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit

                    is PaymentEvent.OnPaymentSuccess -> {
                        val metadata = (event.metadata as ExtendedMetadata.Any).data.let {
                            SendTipMessagePaymentMetadata.unerase(it)
                        }

                        roomController.sendTip(
                            conversationId = metadata.chatId,
                            messageId = metadata.messageId,
                            amount = event.amount,
                            paymentIntentId = event.intentId
                        ).onFailure {
                            event.acknowledge(false) {
                                TopBarManager.showMessage(
                                    TopBarManager.TopBarMessage(
                                        resources.getString(R.string.error_title_failedToSendTip),
                                        resources.getString(
                                            R.string.error_description_failedToSendTip,
                                        )
                                    )
                                )
                            }
                        }.onSuccess {
                            event.acknowledge(true) {

                            }
                        }
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LookupRoom>()
            .map { it.number }
            .map { roomNumber ->
                chatsController.lookupRoom(roomNumber)
                    .onFailure {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToGetRoom),
                                resources.getString(
                                    R.string.error_description_failedToGetRoom,
                                    roomNumber
                                )
                            )
                        )
                    }.onSuccess { (room, members) ->
                        val moderator = members.firstOrNull { it.isModerator }
                        val isMember = members.any { it.isSelf }
                        if (isMember) {
                            dispatchEvent(Event.OpenRoom(room.id))
                        } else {
                            val roomInfo = RoomInfoArgs(
                                roomId = room.id,
                                roomNumber = room.roomNumber,
                                roomTitle = room.titleOrFallback(resources),
                                memberCount = members.count(),
                                ownerId = room.ownerId,
                                hostName = moderator?.identity?.displayName,
                                messagingFeeQuarks = room.messagingFee.quarks,
                            )
                            dispatchEvent(Event.OpenRoomPreview(roomInfo))
                        }
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnJoinRequestedFromSpectating>()
            .map { userManager.authState }
            .onEach {
                if (it is AuthState.LoggedIn) {
                    dispatchEvent(Event.OnJoinRoom)
                } else {
                    dispatchEvent(Event.NeedsAccountCreated)
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnJoinRoom>()
            .filter { stateFlow.value.chattableState is ChattableState.Spectator }
            .map { stateFlow.value.roomInfoArgs }
            .filter { it.ownerId != null }
            .map { profileController.getPaymentDestinationForUser(it.ownerId!!) }
            .mapNotNull {
                if (it.isSuccess) {
                    val paymentDestination = it.getOrNull() ?: return@mapNotNull null
                    val joinChatMetadata = JoinChatPaymentMetadata(
                        userId = userManager.userId!!,
                        chatId = stateFlow.value.conversationId!!
                    )

                    val metadata = ExtendedMetadata.Any(
                        data = joinChatMetadata.erased(),
                        typeUrl = joinChatMetadata.typeUrl
                    )

                    val amount =
                        KinAmount.fromQuarks(stateFlow.value.roomInfoArgs.messagingFeeQuarks)

                    paymentController.presentPublicPaymentConfirmation(
                        amount = amount,
                        destination = paymentDestination,
                        metadata = metadata,
                    )
                } else {
                    return@mapNotNull null
                }
            }.flatMapLatest {
                paymentController.eventFlow.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit

                    is PaymentEvent.OnPaymentSuccess -> {
                        val roomId = stateFlow.value.conversationId.orEmpty()
                        chatsController.joinRoomAsFullMember(roomId, event.intentId)
                            .onFailure {
                                event.acknowledge(false) {
                                    TopBarManager.showMessage(
                                        TopBarManager.TopBarMessage(
                                            resources.getString(R.string.error_title_failedToJoinRoom),
                                            resources.getString(
                                                R.string.error_description_failedToJoinRoom,
                                                stateFlow.value.roomInfoArgs.roomTitle.orEmpty()
                                            )
                                        )
                                    )
                                }
                            }
                            .onSuccess {
                                event.acknowledge(true) {

                                }
                            }
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnShareRoomLink>()
            .map { IntentUtils.shareRoom(stateFlow.value.roomInfoArgs.roomNumber) }
            .onEach { dispatchEvent(Event.ShareRoom(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PromoteUser>()
            .map { roomController.promoteUser(it.conversationId, it.userId) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToPromoteUser),
                            resources.getString(R.string.error_description_failedToPromoteUser)
                        )
                    )
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DemoteUser>()
            .map { roomController.demoteUser(it.conversationId, it.userId) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToDemoteUser),
                            resources.getString(R.string.error_description_failedToDemoteUser)
                        )
                    )
                }
            ).launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatItem>> = stateFlow
        .map { it.conversationId }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { roomController.messages(it).flow }
        .distinctUntilChanged()
        .map { page ->
            val currentState = stateFlow.value // Cache state upfront
            val pointerRefs = currentState.pointerRefs // cache expensive pointer ref map upfront
            val enableReply =
                currentState.replyEnabled && currentState.chattableState is ChattableState.Enabled

            val enableLinkImages = currentState.isLinkImagePreviewsEnabled

            page.map { indice ->
                val (_, message, member, contents, reply, tipInfo) = indice

                println("member=$member")
                val status = findClosestMessageStatus(
                    timestamp = message.id.uuid?.timestamp,
                    statusMap = pointerRefs,
                    fallback = if (contents.isFromSelf) MessageStatus.Sent else MessageStatus.Unknown
                )

                val anchor = if (reply != null) {
                    ReplyMessageAnchor(
                        id = reply.message.id,
                        message = reply.contentEntity,
                        isDeleted = reply.message.isDeleted,
                        deletedBy = reply.message.deletedBy?.let { id ->
                            Deleter(
                                id = id,
                                isSelf = userManager.isSelf(id),
                                isHost = currentState.hostId == message.deletedBy
                            )
                        },
                        sender = Sender(
                            id = reply.message.senderId,
                            profileImage = reply.member?.imageUri.takeIf {
                                it.orEmpty().isNotEmpty()
                            },
                            isFullMember = reply.member?.isFullMember == true,
                            displayName = reply.member?.memberName ?: "Deleted",
                            isSelf = reply.contentEntity.isFromSelf,
                            isBlocked = reply.member?.isBlocked == true,
                            isHost = reply.message.senderId == currentState.hostId && !contents.isFromSelf,
                        )
                    )
                } else {
                    null
                }

                val tippingEnabled =
                    currentState.isTippingEnabled && !userManager.isSelf(message.senderId)

                val tips = if (currentState.isTippingEnabled && tipInfo.isNotEmpty()) {
                    tipInfo.map { (tip, member) ->
                        MessageTip(
                            amount = tip.kin,
                            tipper = Sender(
                                id = member?.id,
                                profileImage = member?.imageUri.nullIfEmpty(),
                                displayName = member?.memberName,
                                isHost = member?.isHost ?: false,
                                isSelf = userManager.isSelf(member?.id),
                                isBlocked = member?.isBlocked ?: false,
                            )
                        )
                    }
                } else {
                    emptyList()
                }

                ChatItem.Message(
                    chatMessageId = message.id,
                    message = contents,
                    date = message.dateMillis.toInstantFromMillis(),
                    status = status,
                    isDeleted = message.isDeleted,
                    deletedBy = if (message.isDeleted) {
                        Deleter(
                            id = message.deletedBy,
                            isSelf = userManager.isSelf(message.deletedBy),
                            isHost = currentState.hostId == message.deletedBy
                        )
                    } else {
                        null
                    },
                    wasSentAsFullMember = !message.sentOffStage,
                    enableMarkup = true,
                    enableReply = enableReply && !message.isDeleted,
                    showTimestamp = false,
                    enableTipping = tippingEnabled,
                    enableLinkImagePreview = enableLinkImages,
                    sender = Sender(
                        id = message.senderId,
                        profileImage = member?.imageUri.takeIf { it.orEmpty().isNotEmpty() },
                        displayName = member?.memberName ?: "Deleted",
                        isSelf = contents.isFromSelf,
                        isFullMember = member?.isFullMember == true,
                        isHost = message.senderId == currentState.hostId,
                        isBlocked = member?.isBlocked == true,
                    ),
                    originalMessage = anchor,
                    messageControls = MessageControls(
                        actions = buildMessageActions(
                            message,
                            member,
                            contents,
                            enableReply,
                            enableTip = tippingEnabled
                        ),
                    ),
                    tips = tips
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .mapLatest { data ->
            var unreadSeparatorInserted = false

            data.insertSeparators { before: ChatItem.Message?, after: ChatItem.Message? ->
                val separators = mutableListOf<ChatItem.Separator>()

                if (
                    stateFlow.value.startAtUnread &&
                    !unreadSeparatorInserted &&
                    after?.chatMessageId?.uuid == stateFlow.value.lastReadMessage &&
                    before?.sender?.isSelf == false
                ) {
                    unreadSeparatorInserted = true
                    val unreadCount = stateFlow.value.unreadCount ?: return@insertSeparators null
                    separators.add(ChatItem.UnreadSeparator(unreadCount))
                }

                val beforeDate = before?.relativeDate
                val afterDate = after?.relativeDate

                // if the date changes between two items, add a date separator
                if (beforeDate != afterDate) {
                    beforeDate?.let { separators.add(ChatItem.Date(before.date)) }
                }

                if (separators.isNotEmpty()) {
                    ChatItem.Separators(separators)
                } else {
                    null
                }
            }
        }.cachedIn(viewModelScope)

    private fun buildMessageActions(
        message: ConversationMessage,
        member: ConversationMember?,
        contents: MessageContent,
        enableReply: Boolean,
        enableTip: Boolean,
    ): List<MessageControlAction> {
        return mutableListOf<MessageControlAction>().apply {
            if (stateFlow.value.isHost) {
                if (member?.memberName?.isNotEmpty() == true && !contents.isFromSelf) {
                    if (member.isFullMember) {
                        add(
                            MessageControlAction.DemoteUser {
                                confirmUserDemote(
                                    conversationId = message.conversationId,
                                    user = member.memberName,
                                    userId = message.senderId
                                )
                            }
                        )
                    } else {
                        add(
                            MessageControlAction.PromoteUser {
                                confirmUserPromote(
                                    conversationId = message.conversationId,
                                    user = member.memberName,
                                    userId = message.senderId
                                )
                            }
                        )
                    }
                }
            }

            if (enableReply) {
                add(
                    MessageControlAction.Reply {
                        val sender = Sender(
                            id = message.senderId,
                            profileImage = member?.imageUri.takeIf { it.orEmpty().isNotEmpty() },
                            displayName = member?.memberName ?: "Deleted",
                            isSelf = contents.isFromSelf,
                            isHost = message.senderId == stateFlow.value.hostId && !contents.isFromSelf,
                            isBlocked = member?.isBlocked == true
                        )
                        val anchor = MessageReplyAnchor(message.id, sender, contents)
                        dispatchEvent(Event.ReplyTo(anchor))
                    }
                )
            }

            if (enableTip) {
                add(
                    MessageControlAction.Tip {
                        dispatchEvent(Event.TipUser(message.id, message.senderId))
                    }
                )
            }

            add(
                MessageControlAction.Copy {
                    dispatchEvent(
                        Event.CopyMessage(
                            contents.localizedText(
                                resources = resources,
                                currencyUtils = currencyUtils
                            )
                        )
                    )
                }
            )
        } + buildSelfDefenseControls(message, member, contents)
    }

    private fun buildSelfDefenseControls(
        message: ConversationMessage,
        member: ConversationMember?,
        contents: MessageContent
    ): List<MessageControlAction> {
        return mutableListOf<MessageControlAction>().apply {
            // delete message
            if (stateFlow.value.isHost || contents.isFromSelf) {
                add(
                    MessageControlAction.Delete {
                        confirmMessageDelete(
                            conversationId = message.conversationId,
                            messageId = message.id
                        )
                    }
                )
            }


            if (stateFlow.value.isHost) {
                if (member?.memberName?.isNotEmpty() == true && !contents.isFromSelf) {
//                    add(
//                        MessageControlAction.RemoveUser(member.memberName.orEmpty()) {
//                            confirmUserRemoval(
//                                conversationId = message.conversationId,
//                                user = member.memberName,
//                                userId = message.senderId,
//                            )
//                        }
//                    )
                    add(
                        MessageControlAction.MuteUser {
                            confirmUserMute(
                                conversationId = message.conversationId,
                                user = member.memberName,
                                userId = message.senderId,
                            )
                        }
                    )
                }
            }

            if (!contents.isFromSelf) {
                if (member?.isBlocked != null) {
                    if (member.isBlocked) {
                        add(
                            MessageControlAction.UnblockUser {
                                dispatchEvent(Event.UnblockUser(member.id))
                            }
                        )
                    } else {
                        add(
                            MessageControlAction.BlockUser {
                                confirmUserBlock(
                                    user = member.memberName,
                                    userId = message.senderId,
                                )
                            }
                        )
                    }
                }

                add(
                    MessageControlAction.ReportUserForMessage(member?.memberName.orEmpty()) {
                        confirmUserReport(
                            user = member?.memberName,
                            userId = message.senderId,
                            messageId = message.id
                        )
                    }
                )
            }
        }.toList()
    }

    private fun confirmMessageDelete(conversationId: ID, messageId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(R.string.title_deleteMessage),
                subtitle = resources.getString(R.string.subtitle_deleteMessage),
                positiveText = resources.getString(R.string.action_delete),
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.DeleteMessage(conversationId, messageId)) },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmUserRemoval(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(R.string.title_removeUserFromRoom, user.orEmpty()),
                subtitle = resources.getString(R.string.subtitle_removeUserFromRoom),
                positiveText = resources.getString(R.string.action_remove),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.RemoveUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmUserMute(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_muteUserInRoom,
                    user.orEmpty().ifEmpty { "User" }),
                subtitle = resources.getString(R.string.subtitle_muteUserInRoom),
                positiveText = resources.getString(R.string.action_mute),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.MuteUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmUserPromote(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_promoteUserInRoom,
                    user.orEmpty().ifEmpty { "User" }),
                subtitle = resources.getString(R.string.subtitle_promoteUserInRoom),
                positiveText = resources.getString(R.string.action_promote),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.PromoteUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.THEMED,
                showScrim = true,
            )
        )
    }

    private fun confirmUserDemote(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_demoteUserInRoom,
                    user.orEmpty().ifEmpty { "User" }),
                subtitle = resources.getString(R.string.subtitle_demoteUserInRoom),
                positiveText = resources.getString(R.string.action_demote),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.DemoteUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmUserBlock(user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_blockUserInRoom,
                    user.orEmpty().ifEmpty { "User" },
                ),
                subtitle = resources.getString(R.string.subtitle_blockUserInRoom, user.orEmpty()),
                positiveText = resources.getString(R.string.action_blockUser, user.orEmpty()),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.BlockUser(userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmUserReport(user: String?, userId: ID, messageId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(R.string.title_reportUserForMessage, user ?: "User"),
                subtitle = resources.getString(R.string.subtitle_reportUserForMessage),
                positiveText = resources.getString(R.string.action_report),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.ReportUser(userId, messageId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    private fun confirmOpenStateChange(conversationId: ID, isRoomOpen: Boolean) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = if (isRoomOpen) resources.getString(R.string.prompt_title_closeRoom) else resources.getString(
                    R.string.prompt_title_reopenRoom
                ),
                subtitle = if (isRoomOpen) resources.getString(R.string.prompt_description_closeRoom) else resources.getString(
                    R.string.prompt_description_reopenRoom
                ),
                positiveText = if (isRoomOpen) resources.getString(R.string.action_closeTemporarily) else resources.getString(
                    R.string.action_reopenRoom
                ),
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = {
                    if (isRoomOpen) {
                        dispatchEvent(Event.OnCloseRoom(conversationId))
                    } else {
                        dispatchEvent(Event.OnOpenRoom(conversationId))
                    }
                },
                type = BottomBarManager.BottomBarMessageType.THEMED,
                showScrim = true,
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        roomController.closeMessageStream()
        viewModelScope.launch {
            stateFlow.value.conversationId?.let {
                roomController.onUserStoppedTypingIn(it)
            }
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatIdChanged -> Timber.d("onChatID changed ${event.chatId?.base58}")
                is Event.OnSelfChanged -> Timber.d("onSelf changed ${event.id?.base58}")
                is Event.OnConversationChanged -> {
                    val members = event.conversationWithPointers.members.count()
                    Timber.d(
                        "conversation changed={id:${event.conversationWithPointers.conversation.id.base58}, " +
                                "unreadCount:${event.conversationWithPointers.conversation.unreadCount}, " +
                                "members:$members, " +
                                "pointers:${event.conversationWithPointers.pointers.count()}"
                    )
                }

                is Event.DeleteMessage -> {
                    Timber.d("Delete Message => ${event.messageId.uuid.toString()}")
                }

                else -> Timber.d("event=${event}")
            }

            when (event) {
                is Event.OnChatIdChanged -> { state ->
                    state.copy(
                        conversationId = event.chatId,
                        textFieldState = TextFieldState()
                    )
                }

                is Event.OnInitialUnreadCountDetermined -> { state -> state.copy(unreadCount = event.count) }

                is Event.OnTitlesChanged -> { state ->
                    state.copy(
                        title = event.title,
                        roomInfoArgs = state.roomInfoArgs.copy(
                            roomTitle = event.roomCardTitle
                        )
                    )
                }

                is Event.OnConversationChanged -> { state ->
                    val (conversation, _, _) = event.conversationWithPointers
                    val members = event.conversationWithPointers.members
                    val host = members.firstOrNull { it.isHost }

                    state.copy(
                        conversationId = conversation.id,
                        imageUri = conversation.imageUri.orEmpty().takeIf { it.isNotEmpty() },
                        pointers = event.conversationWithPointers.pointers,
                        lastReadMessage = state.lastReadMessage
                            ?: findLastReadMessage(event.conversationWithPointers.pointers),
                        pointerRefs = event.conversationWithPointers.pointers
                            .asSequence()
                            .mapNotNull { (key, value) ->
                                key.timestamp?.let { it to (value ?: MessageStatus.Unknown) }
                            }
                            .toMap(),
                        members = members.count(),
                        isRoomOpen = conversation.isOpen,
                        hostId = host?.id,
                        roomInfoArgs = RoomInfoArgs(
                            roomId = conversation.id,
                            roomNumber = conversation.roomNumber,
                            ownerId = conversation.ownerId,
                            hostName = host?.memberName,
                            memberCount = members.count(),
                            messagingFeeQuarks = conversation.coverCharge.quarks
                        )
                    )
                }

                Event.OnSendMessageForFee -> { state ->
                    state.copy(chattableState = ChattableState.TemporarilyEnabled)
                }

                is Event.OnPointersUpdated -> { state ->
                    state.copy(pointers = event.pointers)
                }

                is Event.OnTypingStarted -> { state ->
                    state.copy(showTypingIndicator = true)
                }

                is Event.OnTypingStopped -> { state ->
                    state.copy(showTypingIndicator = false)
                }

                is Event.OnUserTypingStarted -> { state ->
                    state.copy(isSelfTyping = true)
                }

                is Event.OnUserTypingStopped -> { state ->
                    state.copy(isSelfTyping = false)
                }

                is Event.OnShareRoomLink,
                is Event.ShareRoom,
                is Event.OnOpenStateChangedRequested,
                is Event.OnOpenRoom,
                is Event.OnCloseRoom,
                is Event.OnRoomNumberChanged,
                is Event.OnJoinRoom,
                is Event.OnAccountCreated,
                is Event.NeedsAccountCreated,
                is Event.OnJoinRequestedFromSpectating,
                is Event.Error,
                Event.RevealIdentity,
                Event.OnSendCash,
                is Event.MarkRead,
                is Event.MarkDelivered,
                is Event.DeleteMessage,
                is Event.CopyMessage,
                is Event.RemoveUser,
                is Event.ReportUser,
                is Event.MuteUser,
                is Event.BlockUser,
                is Event.UnblockUser,
                is Event.TipUser,
                is Event.PromoteUser,
                is Event.DemoteUser,
                is Event.Resumed,
                is Event.Stopped,
                is Event.LookupRoom,
                is Event.OpenRoomPreview,
                is Event.OpenRoom,
                is Event.OnSendMessage,
                is Event.SendMessage,
                is Event.SendMessageWithFee -> { state -> state }

                is Event.OnUserActivity -> { state ->
                    state.copy(lastSeen = event.activity)
                }

                is Event.OnSelfChanged -> { state ->
                    state.copy(
                        selfId = event.id,
                        selfName = event.displayName
                    )
                }

                is Event.OnAbilityToChatChanged -> { state -> state.copy(chattableState = event.state) }
                is Event.OnReplyEnabled -> { state -> state.copy(replyEnabled = event.enabled) }
                is Event.OnStartAtUnread -> { state -> state.copy(startAtUnread = event.enabled) }
                is Event.ReplyTo -> { state ->
                    state.copy(replyMessage = event.anchor)
                }

                is Event.CancelReply -> { state -> state.copy(replyMessage = null) }
                is Event.OnOpenCloseEnabled -> { state -> state.copy(isOpenCloseEnabled = event.enabled) }
                is Event.OnTippingEnabled -> { state -> state.copy(isTippingEnabled = event.enabled) }
                is Event.OnLinkImagePreviewsEnabled -> { state ->
                    state.copy(
                        isLinkImagePreviewsEnabled = event.enabled
                    )
                }

                Event.OnUnreadStateHandled -> { state -> state.copy(unreadStateHandled = true) }
            }
        }
    }
}

private fun findLastReadMessage(
    statusMap: Map<UUID, MessageStatus?>,
): UUID? {
    return statusMap
        .filter { it.value == MessageStatus.Read }
        .maxByOrNull { it.key.timestamp ?: 0L }
        ?.key
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