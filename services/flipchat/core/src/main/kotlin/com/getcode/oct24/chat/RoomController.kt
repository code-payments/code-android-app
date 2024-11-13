package com.getcode.oct24.chat

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.model.uuid
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.services.model.chat.OutgoingMessageContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.domain.mapper.ConversationMessageWithContentMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContentAndMember
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastPointers
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import javax.inject.Inject

class RoomController @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val conversationMessageWithContentMapper: ConversationMessageWithContentMapper,
) {
    private val db: FcAppDatabase by lazy { FcAppDatabase.requireInstance() }

    fun observeConversation(id: ID): Flow<ConversationWithMembersAndLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    suspend fun getConversation(identifier: ID): ConversationWithMembersAndLastPointers? {
        return db.conversationDao().findConversation(identifier)
    }

    suspend fun getChatMembers(identifier: ID) {
        chatRepository.getChatMembers(ChatIdentifier.Id(identifier))
            .onSuccess { members ->
                val mapped = members.map { conversationMemberMapper.map(identifier to it) }
                db.conversationMembersDao().refreshMembers(identifier, mapped)
            }
    }

    fun openMessageStream(scope: CoroutineScope, identifier: ID) {
        runCatching {
            messagingRepository.openMessageStream(
                coroutineScope = scope,
                chatId = identifier,
                lastMessageId = { db.conversationMessageDao().getNewestMessage(identifier)?.id },
                onMessageUpdate = {
                    scope.launch { db.conversationMessageDao().upsertMessagesWithContent(it) }
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
                        db.conversationPointersDao()
                            .insert(conversationId, messageId.uuid!!, status)
                    }
            }

            MessageStatus.Delivered -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        db.conversationPointersDao()
                            .insert(conversationId, messageId.uuid!!, status)
                    }
            }

            MessageStatus.Read -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        db.conversationPointersDao()
                            .insert(conversationId, messageId.uuid!!, status)
                    }
            }

            MessageStatus.Unknown -> Unit
        }
    }

    suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val output = OutgoingMessageContent.Text(message)
        return messagingRepository.sendMessage(conversationId, output)
            .map { it.id }
    }

    private val pagingConfig = PagingConfig(pageSize = 20)
    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId)

    @OptIn(ExperimentalPagingApi::class)
    fun messages(conversationId: ID): Pager<Int, ConversationMessageWithContentAndMember> = Pager(
        config = pagingConfig,
        remoteMediator = MessagesRemoteMediator(
            conversationId,
            messagingRepository,
            conversationMessageWithContentMapper
        )
    ) {
        conversationPagingSource(conversationId)
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
                db.conversationDao().deleteConversationById(conversationId)
                db.conversationPointersDao().deletePointerForConversation(conversationId)
                db.conversationMessageDao().removeForConversation(conversationId)
            }
    }

    suspend fun deleteMessage(
        conversationId: ID,
        messageId: ID,
    ): Result<Unit> {
        return messagingRepository.deleteMessage(conversationId, messageId)
            .onSuccess {
                db.conversationMessageDao().markDeletedAndRemoveContents(messageId)
            }
    }

    suspend fun removeUser(
        conversationId: ID,
        userId: ID
    ): Result<Unit> {
        return chatRepository.removeUser(conversationId, userId)
            .onSuccess {
                db.conversationMembersDao().removeMemberFromConversation(userId, conversationId)
            }
    }
}

@OptIn(ExperimentalPagingApi::class)
private class MessagesRemoteMediator(
    private val chatId: ID,
    private val repository: MessagingRepository,
    private val conversationMessageWithContentMapper: ConversationMessageWithContentMapper,
) : RemoteMediator<Int, ConversationMessageWithContentAndMember>() {
    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    private var lastFetchedItems: List<ChatMessage>? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationMessageWithContentAndMember>
    ): MediatorResult {
        return try {
            // The network load method takes an optional `after=<user.id>` parameter. For every
            // page after the first, we pass the last user ID to let it continue from where it
            // left off. For REFRESH, pass `null` to load the first page.
            val loadKey = when (loadType) {
                LoadType.REFRESH -> null
                // In this example, we never need to prepend, since REFRESH will always load the
                // first page in the list. Immediately return, reporting end of pagination.
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

                    // We must explicitly check if the last item is `null` when appending,
                    // since passing `null` to networkService is only valid for initial load.
                    // If lastItem is `null` it means no items were loaded after the initial
                    // REFRESH and there are no more items to load.

                    lastItem.message.id
                }
            }
            val query = QueryOptions(
                limit = 20,
                token = loadKey,
                descending = true,
            )

            val response = repository.getMessages(chatId, query)
            val messages = response.getOrNull().orEmpty()
            if (messages == lastFetchedItems) {
                return MediatorResult.Success(endOfPaginationReached = true)
            }

            lastFetchedItems = messages

            val conversationMessagesWithContent =
                messages.map { conversationMessageWithContentMapper.map(chatId to it) }

            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    db.conversationMessageDao().clearMessagesForChat(chatId)
                }

                db.conversationMessageDao()
                    .upsertMessagesWithContent(*conversationMessagesWithContent.toTypedArray())
            }

            MediatorResult.Success(endOfPaginationReached = messages.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}