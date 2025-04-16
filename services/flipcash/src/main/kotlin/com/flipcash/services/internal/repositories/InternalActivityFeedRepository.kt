package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ActivityFeedMessageMapper
import com.flipcash.services.internal.network.services.ActivityFeedService
import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.repository.ActivityFeedRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalActivityFeedRepository(
    private val service: ActivityFeedService,
    private val mapper: ActivityFeedMessageMapper

) : ActivityFeedRepository {
    override suspend fun getLatestNotifications(
        owner: Ed25519.KeyPair,
        type: ActivityFeedType,
        maxItems: Int
    ): Result<List<ActivityFeedMessage>> = service.getLatestNotifications(owner, type, maxItems)
        .onFailure { ErrorUtils.handleError(it) }
        .map { items -> items.map { mapper.map(it) } }
}