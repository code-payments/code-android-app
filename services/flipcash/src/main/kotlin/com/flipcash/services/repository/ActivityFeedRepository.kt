package com.flipcash.services.repository

import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

interface ActivityFeedRepository {
    suspend fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): Result<List<ActivityFeedNotification>>

    suspend fun getPagedNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        queryOptions: QueryOptions,
    ): Result<List<ActivityFeedNotification>>

    suspend fun getBatchedNotifications(
        owner: KeyPair,
        ids: List<ID>
    ): Result<List<ActivityFeedNotification>>
}