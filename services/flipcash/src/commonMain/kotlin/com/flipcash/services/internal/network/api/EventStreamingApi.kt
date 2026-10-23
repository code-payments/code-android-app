package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.events.v1.EventStreamingGrpcKt
import com.codeinc.flipcash.gen.events.v1.EventStreamingService
import com.flipcash.services.internal.annotations.FlipcashManagedStreamingChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventStreamingApi @Inject constructor(
    @FlipcashManagedStreamingChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = EventStreamingGrpcKt.EventStreamingCoroutineStub(managedChannel)

    fun streamEvents(
        requests: Flow<EventStreamingService.StreamEventsRequest>,
    ): Flow<EventStreamingService.StreamEventsResponse> {
        return api.streamEvents(requests)
    }
}
