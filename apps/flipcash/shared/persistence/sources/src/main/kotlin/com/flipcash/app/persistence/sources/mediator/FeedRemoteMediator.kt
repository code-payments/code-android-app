package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.persistence.sources.MessageDataSource
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.QueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPagingApi::class)
class FeedRemoteMediator(
    private val controller: ActivityFeedController,
    private val dataSource: MessageDataSource,
): RemoteMediator<Int, MessageEntity>() {

    private var lastResult = listOf<ActivityFeedNotification>()

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageEntity>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> null

                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

                    lastItem.id
                }
            }

            val queryOptions = QueryOptions(
                token = loadKey,
                limit = state.config.pageSize
            )

            val notifications = controller.queryNotificationsFor(
                ActivityFeedType.TransactionHistory,
                queryOptions
            ).getOrNull().orEmpty()

            if (notifications.isEmpty() || lastResult.any { it.id == notifications.firstOrNull()?.id.orEmpty() }) {
                lastResult = emptyList()
                return MediatorResult.Success(true)
            }

            lastResult = notifications

            withContext(Dispatchers.IO) {
                if (loadType == LoadType.REFRESH) {
                    dataSource.clear()
                }

                dataSource.upsert(notifications)
            }

            MediatorResult.Success(endOfPaginationReached = notifications.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}