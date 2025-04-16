package com.flipcash.services.repository

import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.ActivityFeedType
import com.getcode.ed25519.Ed25519.KeyPair

interface ActivityFeedRepository {
    suspend fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): Result<List<ActivityFeedMessage>>
}