package com.flipcash.services.controllers

import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.repository.ActivityFeedRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject

class ActivityFeedController @Inject constructor(
    private val repository: ActivityFeedRepository,
    private val userManager: UserManager,
) {
    suspend fun getLatestMessagesFor(type: ActivityFeedType, maxItems: Int = -1): Result<List<ActivityFeedMessage>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getLatestNotifications(owner, type, maxItems)
    }
}