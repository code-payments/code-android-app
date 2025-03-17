package xyz.flipchat.chat

import androidx.core.app.NotificationManagerCompat
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.room.withTransaction
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.chat.MessageStatus
import com.getcode.model.uuid
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.utils.base58
import com.getcode.utils.timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.flipchat.chat.paging.MessagingPagingSource
import xyz.flipchat.chat.paging.MessagingRemoteMediator
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.notifications.getRoomNotifications
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers
import xyz.flipchat.services.domain.model.chat.InflatedConversationMessage
import xyz.flipchat.services.domain.model.chat.MessageReactionInfo
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.data.mapper.UserMapper
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

class RoomController @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val userMapper: UserMapper,
    private val conversationMessageMapper: ConversationMessageMapper,
    private val notificationManager: NotificationManagerCompat,
    private val userManager: UserManager,
) {
    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    fun observeConversation(id: ID): Flow<ConversationWithMembersAndLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    suspend fun getConversation(identifier: ID): ConversationWithMembersAndLastPointers? {
        return db.conversationDao().findConversation(identifier)
    }

    suspend fun getUnreadCount(identifier: ID): Int {
        return db.conversationDao().getUnreadCount(identifier) ?: 0
    }

    suspend fun getEmojiReactionsForMessage(messageId: ID): List<MessageReactionInfo> {
        return db.conversationMessageDao().getReactionsForMessage(messageId)
    }

    suspend fun getChatMembers(identifier: ID) {
        chatRepository.getChatMembers(ChatIdentifier.Id(identifier))
            .onSuccess {
                val dbMembers = it.map { m -> conversationMemberMapper.map(identifier to m) }
                val memberIds = dbMembers.map { member -> member.id.base58 }
                val users = it.map { m -> userMapper.map(m) }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationMembersDao().purgeMembersNotIn(identifier, memberIds)
                        db.conversationMembersDao().upsertMembers(*dbMembers.toTypedArray())
                        db.userDao().upsert(*users.toTypedArray())
                    }
                }
            }
    }

    fun openMessageStream(scope: CoroutineScope, identifier: ID) {
        scope.launch {
            val name = db.conversationDao().findConversation(identifier)?.conversation?.title
            val notifications = notificationManager.getRoomNotifications(identifier, name.orEmpty())
            notifications.onEach { notificationId ->
                notificationManager.cancel(notificationId)
            }
        }

        runCatching {
            messagingRepository.openMessageStream(
                coroutineScope = scope,
                chatId = identifier,
                onMessagesUpdated = {
                    scope.launch {
                        db.conversationMessageDao().upsertMessages(identifier, it, userManager.userId)
                    }
                }
            )
        }
    }

    fun closeMessageStream() {
        runCatching { messagingRepository.closeMessageStream() }
    }

    suspend fun resetUnreadCount(conversationId: ID) {
        db.conversationDao().resetUnreadCount(conversationId)
    }

    suspend fun advancePointer(
        conversationId: ID,
        messageId: ID,
        status: MessageStatus
    ) {
        when (status) {
            MessageStatus.Sent -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        withContext(Dispatchers.IO) {
                            db.conversationPointersDao()
                                .insert(conversationId, messageId.uuid!!, status)
                        }
                    }
            }

            MessageStatus.Delivered -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        withContext(Dispatchers.IO) {
                            db.conversationPointersDao()
                                .insert(conversationId, messageId.uuid!!, status)
                        }
                    }
            }

            MessageStatus.Read -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        withContext(Dispatchers.IO) {
                            db.withTransaction {
                                db.conversationPointersDao()
                                    .insert(conversationId, messageId.uuid!!, status)
                                val newest =
                                    db.conversationMessageDao().getNewestMessage(conversationId)
                                if ((messageId.uuid?.timestamp
                                        ?: -1) >= (newest?.id?.uuid?.timestamp ?: 0L)
                                ) {
                                    db.conversationDao().resetUnreadCount(conversationId)
                                }
                            }
                        }
                    }
            }

            MessageStatus.Unknown -> Unit
        }
    }

    suspend fun sendMessage(
        conversationId: ID,
        message: String,
        paymentIntentId: ID? = null
    ): Result<ID> {
        val content = OutgoingMessageContent.Text(message, paymentIntentId)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    suspend fun sendReply(
        conversationId: ID,
        originalMessageId: ID,
        message: String,
        paymentIntentId: ID? = null
    ): Result<ID> {
        val content = OutgoingMessageContent.Reply(originalMessageId, message, paymentIntentId)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    suspend fun sendReaction(
        conversationId: ID,
        messageId: ID,
        emoji: String,
        paymentIntentId: ID? = null
    ): Result<ID> {
        val content = OutgoingMessageContent.Reaction(messageId, emoji, paymentIntentId)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    suspend fun sendTip(
        conversationId: ID,
        messageId: ID,
        amount: KinAmount,
        paymentIntentId: ID
    ): Result<ID> {
        val content = OutgoingMessageContent.Tip(messageId, amount, paymentIntentId)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    private val pagingConfig =
        PagingConfig(pageSize = 25, initialLoadSize = 25, prefetchDistance = 10, enablePlaceholders = true)

    @OptIn(ExperimentalPagingApi::class)
    fun messages(conversationId: ID): Pager<Int, InflatedConversationMessage> =
        Pager(
            config = pagingConfig,
            remoteMediator = MessagingRemoteMediator(
                chatId = conversationId,
                repository = messagingRepository,
                conversationMessageMapper = conversationMessageMapper,
                userId = { userManager.userId }
            )
        ) {
            MessagingPagingSource(
                chatId = conversationId,
                userId = { userManager.userId },
                db = db,
            )
        }

    fun observeTyping(conversationId: ID) = messagingRepository.observeTyping(conversationId)
        .distinctUntilChanged()
        .flatMapLatest { userIds -> db.userDao().getUsersFromIds(userIds) }

    suspend fun onUserStartedTypingIn(conversationId: ID) {
        messagingRepository.onStartedTyping(conversationId)
    }

    suspend fun onUserStillTypingIn(conversationId: ID) {
        messagingRepository.onStillTyping(conversationId)
    }

    suspend fun onUserStoppedTypingIn(conversationId: ID) {
        messagingRepository.onStoppedTyping(conversationId)
    }

    suspend fun leaveRoom(conversationId: ID): Result<Unit> {
        return chatRepository.leaveChat(conversationId)
            .onSuccess {
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao().deleteConversationById(conversationId)
                        db.conversationPointersDao().deletePointerForConversation(conversationId)
                        db.conversationMessageDao().removeForConversation(conversationId)
                    }
                }
            }
    }

    suspend fun setDisplayName(conversationId: ID, displayName: String): Result<Unit> {
        return chatRepository.setDisplayName(conversationId, displayName)
            .onSuccess {
                val conversation = db.conversationDao()
                    .findConversation(conversationId)?.conversation?.copy(title = displayName)
                if (conversation != null) {
                    db.conversationDao().setDisplayName(conversationId, displayName)
                }
            }
    }

    suspend fun deleteMessage(
        conversationId: ID,
        messageId: ID,
    ): Result<Unit> {
        return messagingRepository.deleteMessage(conversationId, messageId)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationMessageDao().markDeleted(messageId, userManager.userId!!)
                }
            }
    }

    suspend fun removeReaction(
        conversationId: ID,
        reactionId: ID,
    ): Result<Unit> {
        return messagingRepository.deleteMessage(conversationId, reactionId)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationMessageDao().removeReaction(reactionId)
                }
            }
    }

    suspend fun removeUser(
        conversationId: ID,
        userId: ID
    ): Result<Unit> {
        return chatRepository.removeUser(conversationId, userId)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationMembersDao().removeMemberFromConversation(userId, conversationId)
                }
            }
    }

    suspend fun reportUserForMessage(
        userId: ID,
        messageId: ID,
    ): Result<Unit> {
        return chatRepository.reportUserForMessage(userId, messageId)
    }

    suspend fun muteUser(
        chatId: ID,
        userId: ID,
    ): Result<Unit> {
        return chatRepository.muteUser(chatId, userId)
    }

    suspend fun blockUser(
        userId: ID,
    ): Result<Unit> {
        withContext(Dispatchers.IO) {
            db.userDao().blockUser(userId)
        }

        return Result.success(Unit)
    }

    suspend fun unblockUser(
        userId: ID,
    ): Result<Unit> {
        withContext(Dispatchers.IO) {
            db.userDao().unblockUser(userId)
        }

        return Result.success(Unit)
    }

    @Deprecated("Replaced by setMessagingFee")
    suspend fun setCoverCharge(
        conversationId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return chatRepository.setCoverCharge(conversationId, amount)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationDao().updateMessagingFee(conversationId, amount.kin)
                }
            }
    }

    suspend fun promoteUser(
        conversationId: ID,
        userId: ID,
    ): Result<Unit> {
        return chatRepository.promoteUser(conversationId, userId)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationMembersDao().promoteMember(conversationId, userId)
                }
            }
    }

    suspend fun demoteUser(
        conversationId: ID,
        userId: ID,
    ): Result<Unit> {
        return chatRepository.demoteUser(conversationId, userId)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationMembersDao().demoteMember(conversationId, userId)
                }
            }
    }

    suspend fun setMessagingFee(
        conversationId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return chatRepository.setMessagingFee(conversationId, amount)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationDao().updateMessagingFee(conversationId, amount.kin)
                }
            }
    }

    suspend fun enableChat(identifier: ID): Result<Unit> {
        return chatRepository.enableChat(identifier)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationDao().enableChatInRoom(identifier)
                }
            }
    }

    suspend fun disableChat(identifier: ID): Result<Unit> {
        return chatRepository.disableChat(identifier)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationDao().disableChatInRoom(identifier)
                }
            }
    }
}

