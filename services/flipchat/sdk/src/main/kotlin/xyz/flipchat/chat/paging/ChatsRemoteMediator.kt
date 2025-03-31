package xyz.flipchat.chat.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.data.Room
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository

@OptIn(ExperimentalPagingApi::class)
internal class ChatsRemoteMediator(
    private val repository: ChatRepository,
    private val conversationMapper: RoomConversationMapper,
) : RemoteMediator<Int, ConversationWithMembersAndLastMessage>() {

    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    private var lastResult = listOf<Room>()

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

            if (rooms.isEmpty() || lastResult.any { it.id == rooms.firstOrNull()?.id.orEmpty() }) {
                lastResult = emptyList()
                return MediatorResult.Success(true)
            }

            lastResult = rooms

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