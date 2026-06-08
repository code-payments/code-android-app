package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPagingApi::class)
class ChatMessageRemoteMediator(
    private val chatId: ChatId,
    private val controller: ChatMessagingController,
    private val dataSource: ChatMessageDataSource,
) : RemoteMediator<Int, ChatMessageEntity>() {

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ChatMessageEntity>,
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> Unit
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> Unit
            }

            val queryOptions = QueryOptions(
                limit = state.config.pageSize,
                descending = true,
            )

            val messages = controller.getMessages(chatId, queryOptions)
                .getOrNull().orEmpty()

            withContext(Dispatchers.IO) {
                dataSource.upsert(chatId, messages)
            }

            MediatorResult.Success(endOfPaginationReached = messages.size < state.config.pageSize)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
