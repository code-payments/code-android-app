package com.getcode.oct24.network.controllers

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.getcode.oct24.db.FcAppDatabase
import com.getcode.oct24.domain.mapper.ConversationMapper
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.network.repository.ChatRepository
import javax.inject.Inject

class ChatsController @Inject constructor(
    chatRepository: ChatRepository,
    conversationMapper: ConversationMapper,
) {
    val db = FcAppDatabase.requireInstance()

    @OptIn(ExperimentalPagingApi::class)
    val chats = Pager(
        config = PagingConfig(pageSize = 20),
        remoteMediator = ChatsRemoteMediator(chatRepository, conversationMapper)
    ) {
        db.conversationDao().observeConversations()
    }

    fun fetch() {

    }
}

@OptIn(ExperimentalPagingApi::class)
class ChatsRemoteMediator(
    private val repository: ChatRepository,
    private val conversationMapper: ConversationMapper,
) : RemoteMediator<Int, Conversation>() {
    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Conversation>
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

                    lastItem.id
                }
            }
            val query = QueryOptions(
                limit = 20,
                token = loadKey,
                descending = true,
            )

            val response = repository.getChats(query)
            val rooms = response.getOrNull().orEmpty()

            val conversations = rooms.map { conversationMapper.map(it) }
            db.withTransaction {
                db.conversationDao().upsertConversations(*conversations.toTypedArray())
            }

            MediatorResult.Success(endOfPaginationReached = rooms.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}