package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.activity.v1.ActivityFeedGrpcKt
import com.codeinc.flipcash.gen.activity.v1.ActivityFeedService
import com.codeinc.flipcash.gen.activity.v1.Model
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.toNotificationIds
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.model.core.ID
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ActivityFeedApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api
        get() = ActivityFeedGrpcKt.ActivityFeedCoroutineStub(managedChannel).withWaitForReady()

    /**
     * Gets the latest [maxItems] notifications in a user's activity feed.
     * Results will be ordered by descending timestamp.
     *
     * @param type The activity feed to fetch notifications from
     * @param maxItems Maximum number of notifications to return. If <= 0, the server default is used.
     */
    suspend fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): ActivityFeedService.GetLatestNotificationsResponse {
        val request = ActivityFeedService.GetLatestNotificationsRequest.newBuilder()
            .setType(Model.ActivityFeedType.forNumber(type.ordinal))
            .setMaxItems(maxItems)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getLatestNotifications(request)
        }
    }

    /**
     * Gets all notifications using a paging API.
     *
     * @param owner The owner of the activity feed
     * @param type The activity feed to fetch notifications from
     * @param queryOptions The paging options
     */
    suspend fun getNotificationsPage(
        owner: KeyPair,
        type: ActivityFeedType,
        queryOptions: QueryOptions,
    ): ActivityFeedService.GetPagedNotificationsResponse {
        val request = ActivityFeedService.GetPagedNotificationsRequest.newBuilder()
            .setType(Model.ActivityFeedType.forNumber(type.ordinal))
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getPagedNotifications(request)
        }
    }

    /**
     * Gets a batch of notifications by ID.
     *
     * @param owner The owner of the activity feed
     * @param ids The notification IDs
     */
    suspend fun getNotificationsByIds(
        owner: KeyPair,
        ids: List<ID>,
    ): ActivityFeedService.GetBatchNotificationsResponse {
        val request = ActivityFeedService.GetBatchNotificationsRequest.newBuilder()
            .apply {
                addAllIds(ids.toNotificationIds())
            }.apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.getBatchNotifications(request)
        }
    }
}