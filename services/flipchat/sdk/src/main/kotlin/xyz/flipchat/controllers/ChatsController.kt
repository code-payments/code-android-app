package xyz.flipchat.controllers

import android.annotation.SuppressLint
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
import com.getcode.model.Kin
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.chat.db.ConversationMemberUpdate
import xyz.flipchat.services.domain.model.chat.db.ConversationUpdate
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatsController @Inject constructor(
    private val conversationMapper: RoomConversationMapper,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val conversationMessageMapper: ConversationMessageMapper,
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
    private val userManager: UserManager,
) {
    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()


    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    val chats: Pager<Int, ConversationWithMembersAndLastMessage> by lazy {
        Pager(
            config = pagingConfig,
            remoteMediator = ChatsRemoteMediator(chatRepository, conversationMapper)
        ) {
            ChatsPagingSource(db)
        }
    }

    suspend fun updateRoom(roomId: ID) {
        chatRepository.getChat(ChatIdentifier.Id(roomId))
            .onSuccess { (room, members) ->
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao().upsertConversations(conversationMapper.map(room))
                        members.map { conversationMemberMapper.map(room.id to it) }.onEach {
                            db.conversationMembersDao().upsertMembers(it)
                        }
                    }
                }

                syncMessagesFromLast(conversationId = roomId)
            }
    }

    fun openEventStream(coroutineScope: CoroutineScope) {
        runCatching {
            chatRepository.openEventStream(coroutineScope) { event ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {

                        db.withTransaction {
                            event.metadata.onEach { update ->
                                when (update) {
                                    is ConversationUpdate.CoverCharge -> {
                                        db.conversationDao().updateCoverCharge(update.roomId, Kin.fromQuarks(update.amount))
                                    }
                                    is ConversationUpdate.DisplayName -> {
                                        db.conversationDao().setDisplayName(update.roomId, update.name)
                                    }
                                    is ConversationUpdate.LastActivity -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao().upsertConversations(
                                                conversation.copy(lastActivity = update.timestamp)
                                            )
                                        }
                                    }
                                    is ConversationUpdate.Refresh -> db.conversationDao().upsertConversations(update.conversation)
                                    is ConversationUpdate.UnreadCount -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao().upsertConversations(
                                                conversation.copy(unreadCount = update.numUnread, hasMoreUnread = update.hasMoreUnread)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        db.withTransaction {
                            event.members.onEach { update ->
                                when (update) {
                                    is ConversationMemberUpdate.FullRefresh -> {
                                        db.conversationMembersDao()
                                            .upsertMembers(*update.members.toTypedArray())
                                    }

                                    is ConversationMemberUpdate.IndividualRefresh -> {
                                        db.conversationMembersDao().upsertMembers(update.member)
                                    }

                                    is ConversationMemberUpdate.Joined -> {
                                        db.conversationMembersDao().upsertMembers(update.member)
                                    }

                                    is ConversationMemberUpdate.Left -> {
                                        db.conversationMembersDao().removeMemberFromConversation(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }

                                    is ConversationMemberUpdate.Muted -> {
                                        db.conversationMembersDao().muteMember(
                                            conversationId = update.roomId,
                                            memberId = update.memberId
                                        )
                                    }

                                    is ConversationMemberUpdate.Removed -> {
                                        db.conversationMembersDao().removeMemberFromConversation(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }
                                }
                            }
                        }
                    }

                    event.message?.let { newMessage ->
                        syncMessagesFromLast(newMessage.conversationId, newMessage)
                    }
                }
            }
        }
    }

    private suspend fun syncMessagesFromLast(
        conversationId: ID,
        newMessage: ConversationMessage? = null
    ) {
        var token: ID?
        if (newMessage != null) {
            // sync between last in DB and this message
            val newestInDb =
                db.conversationMessageDao().getNewestMessage(conversationId)
            if (newestInDb?.id == newMessage.id) {
                withContext(Dispatchers.IO) {
                    db.conversationMessageDao().upsertMessages(newMessage)
                }
                return
            }

            token = newestInDb?.id
        } else {
            val newestInDb =
                db.conversationMessageDao().getNewestMessage(conversationId)
            token = newestInDb?.id
        }

        while (true) {
            val query = QueryOptions(token = token, descending = false, limit = 1_000)
            messagingRepository.getMessages(conversationId, query)
                .onSuccess { syncedMessages ->
                    trace(
                        "synced ${syncedMessages.count()} missing messages for ${conversationId.base58}",
                        type = TraceType.Silent
                    )
                    val messagesWithContent = syncedMessages.map {
                        conversationMessageMapper.map(conversationId to it)
                    }
                    withContext(Dispatchers.IO) {
                        db.conversationMessageDao().upsertMessages(
                            *(messagesWithContent).toTypedArray()
                        )
                    }

                    val nextToken =
                        db.conversationMessageDao().getNewestMessage(conversationId)?.id
                    if (nextToken == token || messagesWithContent.isEmpty()) {
                        return
                    }

                    token = nextToken
                }
                .onFailure {
                    if (newMessage != null) {
                        withContext(Dispatchers.IO) {
                            db.conversationMessageDao().upsertMessages(newMessage)
                        }
                    }
                    return
                }
        }
    }

    fun closeEventStream() {
        runCatching {
            chatRepository.closeEventStream()
        }
    }

    suspend fun lookupRoom(roomNumber: Long): Result<RoomWithMembers> {
        return chatRepository.getChat(identifier = ChatIdentifier.RoomNumber(roomNumber))
    }


    suspend fun createDirectMessage(recipient: ID): Result<RoomWithMembers> {
        return chatRepository.startChat(StartChatRequestType.TwoWay(recipient))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                    }
                }
            }
    }

    suspend fun createGroup(
        title: String? = null,
        participants: List<ID> = emptyList(),
        paymentId: ID,
    ): Result<RoomWithMembers> {
        return chatRepository.startChat(StartChatRequestType.Group(title, participants, paymentId))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                    }
                }
            }
    }

    suspend fun joinRoomAsSpectator(roomId: ID): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.Id(roomId))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                    }
                }
            }
    }

    suspend fun joinRoomAsFullMember(roomId: ID, paymentId: ID?): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.Id(roomId), paymentId)
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                    }
                }
            }
    }

    suspend fun muteRoom(roomId: ID): Result<Unit> {
        return chatRepository.mute(roomId)
            .onSuccess { db.conversationDao().muteChat(roomId) }
    }

    suspend fun unmuteRoom(roomId: ID): Result<Unit> {
        return chatRepository.unmute(roomId)
            .onSuccess { db.conversationDao().unmuteChat(roomId) }
    }
}

private class ChatsPagingSource(
    private val db: FcAppDatabase
) : PagingSource<Int, ConversationWithMembersAndLastMessage>() {

    @SuppressLint("RestrictedApi")
    private val observer =
        ThreadSafeInvalidationObserver(arrayOf("conversations", "messages", "members")) {
            invalidate()
        }

    override fun getRefreshKey(state: PagingState<Int, ConversationWithMembersAndLastMessage>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    @SuppressLint("RestrictedApi")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationWithMembersAndLastMessage> {
        observer.registerIfNecessary(db)
        val currentPage = params.key ?: 0
        val pageSize = params.loadSize
        val offset = currentPage * pageSize

        return withContext(Dispatchers.IO) {
            try {
                val conversations = db.conversationDao().getPagedConversations(pageSize, offset)
                val prevKey = null
                val nextKey = if (conversations.size < pageSize) null else currentPage + 1


                LoadResult.Page(conversations, prevKey, nextKey)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
private class ChatsRemoteMediator(
    private val repository: ChatRepository,
    private val conversationMapper: RoomConversationMapper,
) : RemoteMediator<Int, ConversationWithMembersAndLastMessage>() {

    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationWithMembersAndLastMessage>
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
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(
                            // If we don't have any items, only signal end of pagination
                            // if we've had a refresh
                            endOfPaginationReached = state.pages.isNotEmpty()
                        )

                    lastItem.conversation.id
                }
            }

            val limit = state.config.pageSize

            val query = QueryOptions(
                limit = limit,
                token = loadKey,
                descending = true
            )

            val response = repository.getChats(query)
            val rooms = response.getOrNull().orEmpty()

            if (rooms.isEmpty()) {
                return MediatorResult.Success(true)
            }

            // Map the rooms to your Room entities
            val conversations = rooms.map { conversationMapper.map(it) }

            // Update the database with the new data (upsert)
            withContext(Dispatchers.IO) {
                if (loadType == LoadType.REFRESH) {
                    // Clear all conversations before loading the fresh data
                    db.conversationDao().clearConversations()
                }

                // Insert or update the conversations
                db.conversationDao().upsertConversations(*conversations.toTypedArray())
            }

            MediatorResult.Success(endOfPaginationReached = rooms.size < limit)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}