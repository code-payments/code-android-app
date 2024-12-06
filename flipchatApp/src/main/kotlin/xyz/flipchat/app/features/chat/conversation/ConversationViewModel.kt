package xyz.flipchat.app.features.chat.conversation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import androidx.paging.map
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.model.uuid
import com.getcode.navigation.RoomInfoArgs
import com.getcode.services.model.ExtendedMetadata
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import com.getcode.ui.components.chat.messagecontents.MessageControls
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.formatDateRelatively
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import timber.log.Timber
import xyz.flipchat.app.R
import xyz.flipchat.app.beta.BetaFlag
import xyz.flipchat.app.beta.BetaFlags
import xyz.flipchat.app.features.login.register.onError
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.chat.RoomController
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.JoinChatPaymentMetadata
import xyz.flipchat.services.data.erased
import xyz.flipchat.services.data.typeUrl
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers
import xyz.flipchat.services.extensions.titleOrFallback
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
    clipboardManager: ClipboardManager,
    currencyUtils: CurrencyUtils,
    betaFeatures: BetaFlags,
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
        val unreadCount: Int,
        val chattableState: ChattableState,
        val textFieldState: TextFieldState,
        val replyEnabled: Boolean,
        val replyMessage: ChatItem.Message?,
        val attemptingToFollow: Boolean,
        val title: String,
        val imageUri: String?,
        val lastSeen: Instant?,
        val members: Int?,
        val pointers: Map<UUID, MessageStatus>,
        val showTypingIndicator: Boolean,
        val isSelfTyping: Boolean,
        val roomInfoArgs: RoomInfoArgs,
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
                unreadCount = 0,
                chattableState = ChattableState.Enabled,
                textFieldState = TextFieldState(),
                attemptingToFollow = false,
                replyEnabled = false,
                replyMessage = null,
                title = "",
                lastSeen = null,
                pointers = emptyMap(),
                members = null,
                showTypingIndicator = false,
                isSelfTyping = false,
                roomInfoArgs = RoomInfoArgs(),
            )
        }
    }

    sealed interface Event {
        data object OnClose: Event
        data class OnSelfChanged(val id: ID?, val displayName: String?) : Event
        data class OnChatIdChanged(val chatId: ID?) : Event
        data class OnMembersChanged(val members: List<ConversationMember>): Event
        data class OnConversationChanged(val conversationWithPointers: ConversationWithMembersAndLastPointers) :
            Event

        data class OnUserActivity(val activity: Instant) : Event
        data object SendCash : Event
        data object SendMessage : Event
        data object RevealIdentity : Event

        data class OnAbilityToChatChanged(val state: ChattableState) : Event
        data class OnPointersUpdated(val pointers: Map<UUID, MessageStatus>) : Event
        data class MarkRead(val messageId: ID) : Event
        data class MarkDelivered(val messageId: ID) : Event

        data class OnReplyEnabled(val enabled: Boolean) : Event
        data class ReplyTo(val message: ChatItem.Message) : Event
        data object CancelReply : Event

        data object OnFollowRoom : Event
        data class OnFollowingRoomChanged(val attempting: Boolean): Event
        data object OnJoinRequestedFromSpectating : Event

        data object ReopenStream : Event
        data object CloseStream : Event

        data object OnTypingStarted : Event
        data object OnTypingStopped : Event

        data class CopyMessage(val text: String) : Event
        data class DeleteMessage(val conversationId: ID, val messageId: ID) : Event
        data class RemoveUser(val conversationId: ID, val userId: ID) : Event
        data class ReportUser(val userId: ID, val messageId: ID) : Event
        data class MuteUser(val conversationId: ID, val userId: ID) : Event

        data object OnUserTypingStarted : Event
        data object OnUserTypingStopped : Event

        data class LookupRoom(val number: Long) : Event
        data class OpenJoinConfirmation(val roomInfoArgs: RoomInfoArgs) : Event
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

        betaFeatures.observe(BetaFlag.ReplyToMessage)
            .onEach { dispatchEvent(Event.OnReplyEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .onEach { roomController.getChatMembers(it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatIdChanged>()
            .map { it.chatId }
            .filterNotNull()
            .flatMapLatest { roomController.observeMembersIn(it) }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnMembersChanged(it)) }
            .onEach { members ->
                val selfMember = members.firstOrNull { it.id == userManager.userId }
                val chattableState = if (selfMember != null) {
                    val isMuted = selfMember.isMuted
                    val isSpectator = !selfMember.isFullMember
                    when {
                        isSpectator -> ChattableState.Spectator(
                            KinAmount.fromQuarks(stateFlow.value.roomInfoArgs.coverChargeQuarks)
                        )

                        isMuted -> ChattableState.DisabledByMute
                        else -> ChattableState.Enabled
                    }
                } else {
                    ChattableState.Lurker
                }

                if (stateFlow.value.chattableState != chattableState) {
                    dispatchEvent(Event.OnAbilityToChatChanged(chattableState))
                }
            }
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
            .onEach { roomController.resetUnreadCount(it) }
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
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MarkRead>()
            .onEach { delay(300) }
            .filter { stateFlow.value.chattableState.isMember() }
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
            .filter { stateFlow.value.chattableState.isMember() }
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
            .filterIsInstance<Event.SendMessage>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString().trim()
                if (text.isNotEmpty()) {
                    textFieldState.clearText()

                    val replyingTo = it.replyMessage
                    // TODO: handle replies in the future

                    val message = if (replyingTo != null) {
                        replyingTo.sender.displayName?.let { name -> "@$name: $text" } ?: text
                    } else {
                        text
                    }

                    dispatchEvent(Event.CancelReply)
                    roomController.sendMessage(it.conversationId!!, message)
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
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ReopenStream>()
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
            .filterIsInstance<Event.CloseStream>()
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
            .onEach {
                roomController.onUserStartedTypingIn(it)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnUserTypingStopped>()
            .mapNotNull { stateFlow.value.conversationId }
            .onEach {
                roomController.onUserStoppedTypingIn(it)
            }.launchIn(viewModelScope)

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
            .filterIsInstance<Event.CopyMessage>()
            .map { it.text }
            .onEach {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", it))
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
                                coverChargeQuarks = room.coverCharge.quarks,
                            )
                            dispatchEvent(Event.OpenJoinConfirmation(roomInfo))
                        }
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnFollowRoom>()
            .mapNotNull { stateFlow.value.conversationId }
            .onEach { roomId ->
                dispatchEvent(Event.OnFollowingRoomChanged(true))
                chatsController.joinRoomAsSpectator(roomId)
                    .onFailure {
                        dispatchEvent(Event.OnFollowingRoomChanged(false))
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToFollowRoom),
                                resources.getString(
                                    R.string.error_description_failedToFollowRoom,
                                    stateFlow.value.title
                                )
                            )
                        )
                    }.onSuccess {
                        dispatchEvent(Event.OnFollowingRoomChanged(false))
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnJoinRequestedFromSpectating>()
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
                        KinAmount.fromQuarks(stateFlow.value.roomInfoArgs.coverChargeQuarks)

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
            .filterIsInstance<Event.OnClose>()
            .onEach {
                stateFlow.value.conversationId?.let {
                    roomController.endPreviewIfLurking(it)
                }
            }.launchIn(viewModelScope)
    }

    val messages: Flow<PagingData<ChatItem>> = stateFlow
        .map { it.conversationId }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest {
            roomController.messages(it).flow
        }
        .map { page ->
            page.flatMap { mwc ->
                if (mwc.message.isDeleted) {
                    listOf(
                        ConversationMessageIndice(
                            mwc.message,
                            mwc.member,
                            MessageContent.RawText("", mwc.message.senderId == userManager.userId),
                        )
                    )
                } else {
                    mwc.contents.map {
                        ConversationMessageIndice(
                            mwc.message,
                            mwc.member,
                            it
                        )
                    }
                }
            }
        }
        .map { page ->
            page.map { indice ->
                val (message, member, contents) = indice

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
                    isDeleted = message.isDeleted,
                    showAsChatBubble = true,
                    enableMarkup = true,
                    enableReply = stateFlow.value.replyEnabled && stateFlow.value.chattableState is ChattableState.Enabled,
                    showTimestamp = false, // allow message list to show/hide wrt grouping
                    sender = Sender(
                        id = message.senderId,
                        profileImage = member?.imageUri.takeIf { it.orEmpty().isNotEmpty() },
                        displayName = member?.memberName ?: "Deleted",
                        isSelf = contents.isFromSelf,
                        isHost = message.senderId == stateFlow.value.hostId && !contents.isFromSelf,
                    ),
                    messageControls = MessageControls(
                        actions = listOf(
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
                        ) + buildSelfDefenseControls(message, member, contents),
                    ),
                    key = message.id.uuid.toString()
                )
            }
        }.mapLatest { page ->
            page.insertSeparators { before: ChatItem.Message?, after: ChatItem.Message? ->
                val beforeDate = before?.date?.formatDateRelatively()
                val afterDate = after?.date?.formatDateRelatively()

                if (beforeDate != afterDate) {
                    beforeDate?.let { ChatItem.Date(it) }
                } else {
                    null
                }
            }
        }

    private fun buildSelfDefenseControls(
        message: ConversationMessage,
        member: ConversationMember?,
        contents: MessageContent
    ): List<MessageControlAction> {
        return mutableListOf<MessageControlAction>().apply {
            if (stateFlow.value.isHost) {
//                        add(
//                            MessageControlAction.Delete {
//                                confirmMessageDelete(
//                                    conversationId = message.conversationId,
//                                    messageId = message.id
//                                )
//                            }
//                        )
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
                        MessageControlAction.MuteUser(member.memberName.orEmpty()) {
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

    override fun onCleared() {
        super.onCleared()
        roomController.closeMessageStream()
        viewModelScope.launch {
            stateFlow.value.conversationId?.let {
                if (stateFlow.value.chattableState is ChattableState.Lurker) {
                    roomController.endPreviewIfLurking(it)
                }
                roomController.onUserStoppedTypingIn(it)
            }
        }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatIdChanged -> Timber.d("onChatID changed ${event.chatId?.base58}")
                is Event.OnSelfChanged -> Timber.d("onSelf changed ${event.id?.base58}")
                is Event.OnMembersChanged -> Timber.d("members changed => ${event.members.count()}")
                is Event.OnConversationChanged -> {
                    val members = event.conversationWithPointers.members.count()
                    Timber.d(
                        "conversation changed={id:${event.conversationWithPointers.conversation.id.base58}, " +
                                "unreadCount:${event.conversationWithPointers.conversation.unreadCount}, " +
                                "members:$members, " +
                                "pointers:${event.conversationWithPointers.pointers.count()}"
                    )
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

                is Event.OnMembersChanged -> { state ->
                    val members = event.members
                    val host = members.firstOrNull { it.isHost }
                    state.copy(
                        hostId = host?.id,
                        members = members.count().takeIf { it > 0 },
                        roomInfoArgs = state.roomInfoArgs.copy(
                            hostName = host?.memberName,
                            memberCount = members.count(),
                        )
                    )
                }

                is Event.OnConversationChanged -> { state ->
                    val (conversation, _, _) = event.conversationWithPointers
                    val members = event.conversationWithPointers.members
                    val host = members.firstOrNull { it.isHost }
                    state.copy(
                        conversationId = conversation.id,
                        unreadCount = conversation.unreadCount,
                        imageUri = conversation.imageUri.orEmpty().takeIf { it.isNotEmpty() },
                        title = conversation.title,
                        pointers = event.conversationWithPointers.pointers,
                        members = event.conversationWithPointers.members.count(),
                        hostId = host?.id,
                        roomInfoArgs = RoomInfoArgs(
                            roomId = conversation.id,
                            roomNumber = conversation.roomNumber,
                            roomTitle = conversation.title,
                            ownerId = conversation.ownerId,
                            hostName = host?.memberName,
                            memberCount = members.count(),
                            coverChargeQuarks = conversation.coverChargeQuarks ?: 0,
                        )
                    )
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

                is Event.OnClose,
                is Event.OnJoinRequestedFromSpectating,
                is Event.OnFollowRoom,
                is Event.Error,
                Event.RevealIdentity,
                Event.SendCash,
                is Event.MarkRead,
                is Event.MarkDelivered,
                is Event.DeleteMessage,
                is Event.CopyMessage,
                is Event.RemoveUser,
                is Event.ReportUser,
                is Event.MuteUser,
                is Event.ReopenStream,
                is Event.CloseStream,
                is Event.LookupRoom,
                is Event.OpenJoinConfirmation,
                is Event.OpenRoom,
                is Event.SendMessage -> { state -> state }

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
                is Event.ReplyTo -> { state ->
                    state.copy(replyMessage = event.message)
                }

                is Event.CancelReply -> { state -> state.copy(replyMessage = null) }
                is Event.OnFollowingRoomChanged -> { state -> state.copy(attemptingToFollow = event.attempting) }
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