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
import com.getcode.utils.base58
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.Room
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.query.QueryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.domain.mapper.ConversationMessageWithContentMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContentAndMember
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.db.ConversationDao
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatsController @Inject constructor(
    private val conversationMapper: RoomConversationMapper,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val conversationMessageWithContentMapper: ConversationMessageWithContentMapper,
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
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
                db.conversationDao().upsertConversations(conversationMapper.map(room))
                members.map { conversationMemberMapper.map(room.id to it) }.onEach {
                    db.conversationMembersDao().upsertMembers(it)
                }

                val newestMessageInRoom = db.conversationMessageDao().getNewestMessage(roomId)?.id
                messagingRepository.getMessages(
                    room.id,
                    queryOptions = QueryOptions(
                        limit = 1,
                        token = newestMessageInRoom
                    )
                ).onSuccess { messages ->
                    val newest = messages.maxByOrNull { it.dateMillis }
                    if (newest != null) {
                        val conversationMessage =
                            conversationMessageWithContentMapper.map(room.id to newest)
                        db.conversationMessageDao()
                            .upsertMessagesWithContent(conversationMessage)
                    }
                }
            }
    }

    fun openEventStream(coroutineScope: CoroutineScope) {
        runCatching {
            chatRepository.openEventStream(coroutineScope) { event ->
                coroutineScope.launch {
                    if (event.conversations.isNotEmpty()) {
                        db.conversationDao()
                            .upsertConversations(*event.conversations.toTypedArray())
                    }
                    if (event.messages.isNotEmpty()) {
                        db.conversationMessageDao()
                            .upsertMessagesWithContent(*event.messages.toTypedArray())
                    }

                    if (event.members.isNotEmpty()) {
                        db.conversationMembersDao().upsertMembers(*event.members.toTypedArray())
                    }
                }
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

    suspend fun createDirectMessage(recipient: ID): Result<Room> {
        return chatRepository.startChat(StartChatRequestType.TwoWay(recipient))
            .onSuccess {
                db.conversationDao().upsertConversations(conversationMapper.map(it))
            }
    }

    suspend fun createGroup(
        title: String? = null,
        participants: List<ID> = emptyList(),
        paymentId: ID,
    ): Result<Room> {
        return chatRepository.startChat(StartChatRequestType.Group(title, participants, paymentId))
            .onSuccess {
                db.conversationDao().upsertConversations(conversationMapper.map(it))
            }
    }

    suspend fun joinRoom(roomId: ID, paymentId: ID?): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.Id(roomId), paymentId)
            .onSuccess {
                db.conversationDao().upsertConversations(conversationMapper.map(it.room))
            }
    }

    suspend fun joinRoom(roomNumber: Long, paymentId: ID): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.RoomNumber(roomNumber), paymentId)
            .onSuccess {
                db.conversationDao().upsertConversations(conversationMapper.map(it.room))
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
): PagingSource<Int, ConversationWithMembersAndLastMessage>() {

    @SuppressLint("RestrictedApi")
    private val observer = ThreadSafeInvalidationObserver(arrayOf("conversations", "messages", "members")) {
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