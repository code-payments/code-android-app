@file:OptIn(ExperimentalPagingApi::class)

package com.flipcash.shared.chat.internal.delegates

import androidx.core.app.NotificationManagerCompat
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.persistence.sources.mediator.ChatMessageRemoteMediator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.shared.chat.MessagingOperations
import com.flipcash.shared.chat.NoDmChatInitializedException
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.services.user.UserManager
import com.getcode.utils.decodeBase58
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Handles all per-chat messaging operations: sending and receiving messages,
 * read-pointer advancement, paging, member identity resolution, and notification
 * management.
 *
 * This delegate is self-contained — it does not emit cross-delegate events.
 * All methods target a single conversation identified by [ChatId].
 *
 * Message sending follows an optimistic-insert pattern:
 * 1. [insertPending][ChatMessageDataSource.insertPending] writes a local placeholder.
 * 2. The server call returns the confirmed [ChatMessage].
 * 3. [confirmPending][ChatMessageDataSource.confirmPending] replaces the placeholder,
 *    or [failPending][ChatMessageDataSource.failPending] marks it as failed.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
@Singleton
class MessagingDelegate @Inject constructor(
    private val chatController: ChatController,
    private val messagingController: ChatMessagingController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val contactDataSource: ContactDataSource,
    private val notificationManager: NotificationManagerCompat,
    private val userManager: UserManager,
    private val stateHolder: ChatStateHolder,
) : MessagingOperations {

    // region MessagingOperations

    override suspend fun getChatId(contact: DeviceContact): Result<ChatId> {
        val raw = contactDataSource.getDmChatId(contact.e164)
        if (raw.isNullOrEmpty()) {
            return Result.failure(NoDmChatInitializedException(contact.e164))
        }
        return runCatching { ChatId(raw.decodeBase58()) }
    }

    override suspend fun getOtherMemberE164(chatId: ChatId): String? {
        val selfId = userManager.accountId
        val localMembers = memberDataSource.getMembersForChat(chatId)
        val otherMember = localMembers.firstOrNull { it.userId != selfId }
        if (otherMember != null) return otherMember.userProfile.verifiedPhoneNumber

        val metadata = chatController.getChat(chatId).getOrNull() ?: return null
        memberDataSource.upsert(chatId, metadata.members)
        return metadata.members
            .firstOrNull { it.userId != selfId }
            ?.userProfile?.verifiedPhoneNumber
    }

    override fun setActiveChatId(chatId: ChatId?) {
        stateHolder.update { it.copy(activeChat = chatId) }
    }

    override fun isActiveChat(chatId: ChatId): Boolean {
        return stateHolder.current.activeChat == chatId
    }

    override fun dismissNotifications(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
    }

    override fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    override fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(pageSize = 50),
            remoteMediator = ChatMessageRemoteMediator(chatId, messagingController, messageDataSource),
        ) {
            messageDataSource.observeForChat(chatId)
        }.flow.map { page ->
            page.map { entity -> messageDataSource.toChatMessage(entity) }
        }
    }

    override fun observeMembers(chatId: ChatId): Flow<List<ChatMember>> {
        return memberDataSource.observeMembers(chatId)
    }

    override fun observeOtherReadPointer(chatId: ChatId): Flow<MessagePointer?> {
        val selfId = userManager.accountId
        return memberDataSource.observeMembers(chatId)
            .map { members ->
                members.firstOrNull { it.userId != selfId }
                    ?.pointers
                    ?.firstOrNull { it.type == PointerType.READ }
            }
            .distinctUntilChanged()
    }

    override suspend fun loadMessages(chatId: ChatId) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)

                val latest = messages.maxByOrNull { it.messageId } ?: return@onSuccess
                metadataDataSource.updateLastMessageId(chatId, latest.messageId)
                metadataDataSource.updateLastActivity(chatId, latest.timestamp.toEpochMilliseconds())
            }
    }

    override suspend fun sendMessage(chatId: ChatId, content: String): Result<ChatMessage> {
        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send message without an account"))

        val content = listOf(MessageContent.Text(content))
        val (_, clientMessageId) = messageDataSource.insertPending(
            chatId = chatId,
            content = content,
            senderId = senderId,
        )

        return messagingController.sendMessage(chatId, content, clientMessageId)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage)
                advanceReadPointer(chatId, serverMessage.messageId)

                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    override suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit> {
        val selfId = userManager.accountId ?: return Result.failure(
            IllegalStateException("No account")
        )

        val pointer = MessagePointer(
            type = PointerType.READ,
            userId = selfId,
            value = messageId,
            timestamp = Clock.System.now(),
        )
        memberDataSource.updatePointers(chatId, pointer)

        return messagingController.advancePointer(chatId, PointerType.READ, messageId)
    }

    override suspend fun markAsRead(chatId: ChatId): Result<Unit> {
        val messageId = stateHolder.current.feed
            .firstOrNull { it.chatId == chatId }
            ?.lastMessage?.messageId
            ?: messageDataSource.getLatestMessageId(chatId)
            ?: return Result.success(Unit)
        return advanceReadPointer(chatId, messageId)
            .also { dismissNotifications(chatId) }
    }

    override suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit> {
        return messagingController.notifyIsTyping(chatId, typingState)
    }

    // endregion

    // region Internal

    internal suspend fun clear() {
        metadataDataSource.clear()
        messageDataSource.clear()
        memberDataSource.clear()
    }

    // endregion
}
