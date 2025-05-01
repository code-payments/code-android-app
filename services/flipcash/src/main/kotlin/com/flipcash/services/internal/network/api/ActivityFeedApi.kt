package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.activity.v1.ActivityFeedGrpc
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
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ActivityFeedApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api
        get() = ActivityFeedGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Gets the latest [maxItems] notifications in a user's activity feed.
     * Results will be ordered by descending timestamp.
     *
     * @param type The activity feed to fetch notifications from
     * @param maxItems Maximum number of notifications to return. If <= 0, the server default is used.
     */
    fun getLatestNotifications(
        owner: KeyPair,
        type: ActivityFeedType,
        maxItems: Int = -1,
    ): Flow<ActivityFeedService.GetLatestNotificationsResponse> {
        val request = ActivityFeedService.GetLatestNotificationsRequest.newBuilder()
            .setType(Model.ActivityFeedType.forNumber(type.ordinal))
            .setMaxItems(maxItems)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getLatestNotifications
            .callAsCancellableFlow(request)
    }

    /**
     * Gets all notifications using a paging API.
     *
     * @param owner The owner of the activity feed
     * @param type The activity feed to fetch notifications from
     * @param queryOptions The paging options
     */
    fun getNotificationsPage(
        owner: KeyPair,
        type: ActivityFeedType,
        queryOptions: QueryOptions,
    ): Flow<ActivityFeedService.GetPagedNotificationsResponse> {
        val request = ActivityFeedService.GetPagedNotificationsRequest.newBuilder()
            .setType(Model.ActivityFeedType.forNumber(type.ordinal))
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return api::getPagedNotifications
            .callAsCancellableFlow(request)
    }

    /**
     * Gets a batch of notifications by ID.
     *
     * @param owner The owner of the activity feed
     * @param ids The notification IDs
     */
    fun getNotificationsByIds(
        owner: KeyPair,
        ids: List<ID>,
    ): Flow<ActivityFeedService.GetBatchNotificationsResponse> {
        val request = ActivityFeedService.GetBatchNotificationsRequest.newBuilder()
            .apply {
                addAllIds(ids.toNotificationIds())
            }.apply {
                setAuth(authenticate(owner))
            }.build()

        return api::getBatchNotifications
            .callAsCancellableFlow(request)
    }
}