package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.activity.v1.ActivityFeedService
import com.codeinc.flipcash.gen.activity.v1.Model
import com.flipcash.services.internal.network.api.ActivityFeedApi
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.GetActivityFeedMessagesError
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

internal class ActivityFeedService @Inject constructor(
    private val api: ActivityFeedApi,
) {
    suspend fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): Result<List<Model.Notification>> {
        return runCatching {
            api.getLatestNotifications(owner, type, maxItems)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    ActivityFeedService.GetLatestNotificationsResponse.Result.OK -> Result.success(response.notificationsList)
                    ActivityFeedService.GetLatestNotificationsResponse.Result.DENIED -> Result.failure(GetActivityFeedMessagesError.Denied())
                    ActivityFeedService.GetLatestNotificationsResponse.Result.UNRECOGNIZED -> Result.failure(GetActivityFeedMessagesError.Unrecognized())
                    else -> Result.failure(GetActivityFeedMessagesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetActivityFeedMessagesError.Other(cause = cause))
            }
        )
    }

    suspend fun getNotificationsPage(
        owner: KeyPair,
        type: ActivityFeedType,
        queryOptions: QueryOptions,
    ): Result<List<Model.Notification>> {
        return runCatching {
            api.getNotificationsPage(owner, type, queryOptions)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    ActivityFeedService.GetPagedNotificationsResponse.Result.OK -> Result.success(response.notificationsList)
                    ActivityFeedService.GetPagedNotificationsResponse.Result.DENIED -> Result.failure(GetActivityFeedMessagesError.Denied())
                    ActivityFeedService.GetPagedNotificationsResponse.Result.UNRECOGNIZED -> Result.failure(GetActivityFeedMessagesError.Unrecognized())
                    else -> Result.failure(GetActivityFeedMessagesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetActivityFeedMessagesError.Other(cause = cause))
            }
        )
    }

    suspend fun getNotificationsByIds(
        owner: KeyPair,
        ids: List<ID>
    ): Result<List<Model.Notification>> {
        return runCatching {
            api.getNotificationsByIds(owner, ids)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    ActivityFeedService.GetBatchNotificationsResponse.Result.OK -> Result.success(response.notificationsList)
                    ActivityFeedService.GetBatchNotificationsResponse.Result.DENIED -> Result.failure(GetActivityFeedMessagesError.Denied())
                    ActivityFeedService.GetBatchNotificationsResponse.Result.NOT_FOUND -> Result.failure(GetActivityFeedMessagesError.NotFound())
                    ActivityFeedService.GetBatchNotificationsResponse.Result.UNRECOGNIZED -> Result.failure(GetActivityFeedMessagesError.Unrecognized())
                    else -> Result.failure(GetActivityFeedMessagesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetActivityFeedMessagesError.Other(cause = cause))
            }
        )
    }
}