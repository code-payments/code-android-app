package com.flipcash.app.activityfeed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.persistence.sources.MessageDataSource
import com.flipcash.app.persistence.sources.mapper.MessageEntityToFeedMessageMapper
import com.flipcash.app.persistence.sources.mediator.FeedRemoteMediator
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.NotificationState
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.user.UserManager
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityFeedCoordinator @Inject constructor(
    private val activityFeedController: ActivityFeedController,
    private val dataSource: MessageDataSource,
    private val mapper: MessageEntityToFeedMessageMapper,
    userManager: UserManager,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    private val _messages: Flow<PagingData<ActivityFeedMessage>> = userManager.state
        .filter { it.authState.canAccessAuthenticatedApis }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = FeedRemoteMediator(activityFeedController, dataSource)
            ) {
                dataSource.observe()
            }.flow.map { page -> page.map { entity -> mapper.map(entity) } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ActivityFeedMessage>> = userManager.state
        .mapNotNull { it.authState }
        .filter { it.canAccessAuthenticatedApis }
        .flatMapLatest { _messages }

    suspend fun checkPendingMessagesForUpdates(): Result<Int> {
        val pendingMessages = dataSource.query(whereClause = "state = '${NotificationState.PENDING.name}'")

        if (pendingMessages.isEmpty()) {
            trace(
                tag = "ActivityFeedCoordinator",
                message = "No pending messages found",
                type = TraceType.Silent
            )
            return Result.success(0)
        }

        return activityFeedController.getNotificationsById(pendingMessages.map { it.id })
            .map { notifications ->
                val completedCount = notifications.count { it.state == NotificationState.COMPLETED }

                trace(
                    tag = "ActivityFeedCoordinator",
                    message = "$completedCount notifications completed from batch",
                    type = TraceType.Silent
                )

                dataSource.upsert(notifications)
                completedCount
            }
    }

    suspend fun fetchSinceLatest(count: Int = 20): Result<Unit> {
        val latest = dataSource.getMostRecent()
        return activityFeedController.queryNotificationsFor(
            type = ActivityFeedType.TransactionHistory,
            queryOptions = QueryOptions(
                limit = count,
                token = latest?.id,
                descending = false
            )
        ).onSuccess { dataSource.upsert(it) }.map { Unit }
    }
}