package xyz.flipchat.chat

import android.annotation.SuppressLint
import androidx.core.app.NotificationManagerCompat
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.paging.util.ThreadSafeInvalidationObserver
import androidx.room.withTransaction
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.uuid
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.utils.base58
import com.getcode.utils.timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.notifications.getRoomNotifications
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndContent
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithMemberAndContentAndReplyAndTips
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

class RoomController @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val conversationMessageMapper: ConversationMessageMapper,
    private val notificationManager: NotificationManagerCompat,
    private val userManager: UserManager,
) {
    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    fun observeConversation(id: ID): Flow<ConversationWithMembersAndLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    fun observeMembersIn(id: ID): Flow<List<ConversationMember>> {
        return db.conversationMembersDao().observeMembersIn(id)
    }

    suspend fun getConversation(identifier: ID): ConversationWithMembersAndLastPointers? {
        return db.conversationDao().findConversation(identifier)
    }

    suspend fun getMessage(id: ID): ConversationMessageWithMemberAndContent? {
        return db.conversationMessageDao().getMessageWithContentById(id, userManager.userId)
    }

    suspend fun getUnreadCount(identifier: ID): Int {
        return db.conversationDao().getUnreadCount(identifier) ?: 0
    }

    suspend fun getChatMembers(identifier: ID) {
        chatRepository.getChatMembers(ChatIdentifier.Id(identifier))
            .onSuccess {
                val dbMembers = it.map { m -> conversationMemberMapper.map(identifier to m) }
                val memberIds = dbMembers.map { member -> member.id.base58 }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationMembersDao().purgeMembersNotIn(identifier, memberIds)
                        db.conversationMembersDao().upsertMembers(*dbMembers.toTypedArray())
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
                    scope.launch { db.conversationMessageDao().upsertMessages(it) }
                },
                onMessagesDeleted = { messages ->
                    scope.launch {
                        messages.onEach {
                            db.conversationMessageDao().markDeleted(it.first, it.second)
                        }
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

    suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val content = OutgoingMessageContent.Text(message)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    suspend fun sendReply(conversationId: ID, originalMessageId: ID, message: String): Result<ID> {
        val content = OutgoingMessageContent.Reply(originalMessageId, message)
        return messagingRepository.sendMessage(conversationId, content)
            .map { it.id }
    }

    suspend fun sendReaction(conversationId: ID, messageId: ID, emoji: String): Result<ID> {
        val content = OutgoingMessageContent.Reaction(messageId, emoji)
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
        PagingConfig(pageSize = 25, initialLoadSize = 25, prefetchDistance = 10)

    @OptIn(ExperimentalPagingApi::class)
    fun messages(conversationId: ID): Pager<Int, ConversationMessageWithMemberAndContentAndReplyAndTips> =
        Pager(
            config = pagingConfig,
            remoteMediator = MessagesRemoteMediator(
                chatId = conversationId,
                repository = messagingRepository,
                conversationMessageMapper = conversationMessageMapper
            )
        ) {
            MessagingPagingSource(conversationId, { userManager.userId }, db)
        }

    val typingChats = chatRepository.typingChats

    fun observeTyping(conversationId: ID) = chatRepository.observeTyping(conversationId)

    suspend fun onUserStartedTypingIn(conversationId: ID) {
        messagingRepository.onStartedTyping(conversationId)
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
            db.conversationMembersDao().blockMember(userId)
        }

        return Result.success(Unit)
    }

    suspend fun unblockUser(
        userId: ID,
    ): Result<Unit> {
        withContext(Dispatchers.IO) {
            db.conversationMembersDao().unblockMember(userId)
        }

        return Result.success(Unit)
    }

    suspend fun setCoverCharge(
        conversationId: ID,
        amount: KinAmount
    ): Result<Unit> {
        return chatRepository.setCoverCharge(conversationId, amount)
            .onSuccess {
                withContext(Dispatchers.IO) {
                    db.conversationDao().updateCoverCharge(conversationId, amount.kin)
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

private class MessagingPagingSource(
    private val chatId: ID,
    private val userId: () -> ID?,
    private val db: FcAppDatabase
) : PagingSource<Int, ConversationMessageWithMemberAndContentAndReplyAndTips>() {

    @SuppressLint("RestrictedApi")
    private val observer =
        ThreadSafeInvalidationObserver(arrayOf("conversations", "messages", "members")) {
            invalidate()
        }

    override fun getRefreshKey(state: PagingState<Int, ConversationMessageWithMemberAndContentAndReplyAndTips>): Int? {
        return null
    }

    @SuppressLint("RestrictedApi")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationMessageWithMemberAndContentAndReplyAndTips> {
        observer.registerIfNecessary(db)
        val currentPage = params.key ?: 0
        val pageSize = params.loadSize
        val offset = currentPage * pageSize

        return withContext(Dispatchers.Default) {
            try {
                val unmappedMessages =
                    db.conversationMessageDao()
                        .getPagedMessagesWithRepliesAndTips(chatId, pageSize, offset, userId())

                val messages = unmappedMessages.mapNotNull {
                    val content = MessageContent.fromData(
                        it.message.type,
                        it.message.content,
                        isFromSelf = it.message.senderIdBase58 == userId()?.base58
                    ) ?: return@mapNotNull null

                    ConversationMessageWithMemberAndContentAndReplyAndTips(
                        message = it.message,
                        member = it.member,
                        content = content,
                        reply = it.reply,
                        tips = it.tips
                    )
                }

                val prevKey = null
                val nextKey = if (messages.size < pageSize) null else currentPage + 1

                LoadResult.Page(
                    data = messages,
                    prevKey = prevKey,
                    nextKey = nextKey,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
private class MessagesRemoteMediator(
    private val chatId: ID,
    private val repository: MessagingRepository,
    private val conversationMessageMapper: ConversationMessageMapper,
) : RemoteMediator<Int, ConversationMessageWithMemberAndContentAndReplyAndTips>() {

    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationMessageWithMemberAndContentAndReplyAndTips>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> {
                    null
                }

                LoadType.PREPEND -> {
                    return MediatorResult.Success(true)  // Don't load newer messages
                }

                LoadType.APPEND -> {
                    // Get the last item from our data
                    state.lastItemOrNull()?.message?.id
                }
            }

            val limit = state.config.pageSize

            val query = QueryOptions(
                limit = limit,
                token = loadKey,
                descending = true
            )

            val response = withContext(Dispatchers.IO) { repository.getMessages(chatId, query) }
            val messages = response.getOrNull().orEmpty()

            if (messages.isEmpty()) {
                return MediatorResult.Success(true)
            }

            val conversationMessages =
                messages.map { conversationMessageMapper.map(chatId to it) }

            withContext(Dispatchers.IO) {
                if (loadType == LoadType.REFRESH) {
                    db.conversationMessageDao().clearMessagesForChat(chatId)
                }

                db.conversationMessageDao()
                    .upsertMessages(*conversationMessages.toTypedArray())
            }

            MediatorResult.Success(endOfPaginationReached = messages.size < limit)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}