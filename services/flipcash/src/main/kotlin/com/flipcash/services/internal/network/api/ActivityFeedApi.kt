package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.activity.v1.ActivityFeedGrpc
import com.codeinc.flipcash.gen.activity.v1.ActivityFeedService
import com.codeinc.flipcash.gen.activity.v1.Model
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.ActivityFeedType
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
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
}