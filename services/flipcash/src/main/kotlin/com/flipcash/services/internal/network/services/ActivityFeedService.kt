package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.activity.v1.ActivityFeedService
import com.codeinc.flipcash.gen.activity.v1.Model
import com.flipcash.services.internal.network.api.ActivityFeedApi
import com.flipcash.services.internal.network.managedApiRequest
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.GetActivityFeedMessagesError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import javax.inject.Inject

internal class ActivityFeedService @Inject constructor(
    private val api: ActivityFeedApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): Result<List<Model.Notification>> {
        return networkOracle.managedApiRequest(
            call = { api.getLatestNotifications(owner, type, maxItems) },
            handleResponse = { response ->
                when (response.result) {
                    ActivityFeedService.GetLatestNotificationsResponse.Result.OK -> Result.success(response.notificationsList)
                    ActivityFeedService.GetLatestNotificationsResponse.Result.DENIED -> Result.failure(GetActivityFeedMessagesError.Denied())
                    ActivityFeedService.GetLatestNotificationsResponse.Result.UNRECOGNIZED -> Result.failure(GetActivityFeedMessagesError.Unrecognized())
                    else -> Result.failure(GetActivityFeedMessagesError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(GetActivityFeedMessagesError.Other(cause = cause))
            }
        )
    }
}