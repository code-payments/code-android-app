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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

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
            val token = when (loadType) {
                LoadType.REFRESH -> null
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)
                    lastItem.messageId.toPagingToken()
                }
            }

            val queryOptions = QueryOptions(
                limit = state.config.pageSize,
                token = token,
                descending = true,
            )

            val messages = controller.getMessages(chatId, queryOptions)
                .getOrThrow()

            withContext(Dispatchers.IO) {
                dataSource.upsert(chatId, messages)
            }

            MediatorResult.Success(endOfPaginationReached = messages.size < state.config.pageSize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Reported, not folded into end-of-pagination: a failed page is not the start of the chat.
            MediatorResult.Error(e)
        }
    }
}

private fun Long.toPagingToken(): List<Byte> {
    return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array().toList()
}
