package com.flipcash.services.controllers

import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.ActivityFeedRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

class ActivityFeedController @Inject constructor(
    private val repository: ActivityFeedRepository,
    private val userManager: UserManager,
) {
    suspend fun getLatestNotificationsFor(type: ActivityFeedType, maxItems: Int = -1): Result<List<ActivityFeedNotification>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getLatestNotifications(owner, type, maxItems)
    }

    suspend fun queryNotificationsFor(type: ActivityFeedType, queryOptions: QueryOptions): Result<List<ActivityFeedNotification>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getPagedNotifications(owner, type, queryOptions)
    }

    suspend fun getNotificationsById(ids: List<ID>): Result<List<ActivityFeedNotification>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getBatchedNotifications(owner, ids)
    }
}