package xyz.flipchat.chat.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.getcode.model.ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.model.chat.InflatedConversationMessage
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository

@OptIn(ExperimentalPagingApi::class)
internal class MessagingRemoteMediator(
    private val chatId: ID,
    private val repository: MessagingRepository,
    private val conversationMessageMapper: ConversationMessageMapper,
    private val userId: () -> ID?
) : RemoteMediator<Int, InflatedConversationMessage>() {

    private val db = FcAppDatabase.requireInstance()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, InflatedConversationMessage>
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

            val conversationMessages =
                messages.map { conversationMessageMapper.map(chatId to it) }

            if (conversationMessages.isEmpty()) {
                return MediatorResult.Success(true)
            }

            withContext(Dispatchers.IO) {
                if (loadType == LoadType.REFRESH) {
                    db.conversationMessageDao().clearMessagesForChat(chatId)
                }

                db.conversationMessageDao()
                    .upsertMessages(messages = conversationMessages, selfID = userId())
            }

            MediatorResult.Success(endOfPaginationReached = messages.size < limit)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}