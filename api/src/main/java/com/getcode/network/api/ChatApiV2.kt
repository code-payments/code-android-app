package com.getcode.network.api

import com.codeinc.gen.chat.v2.ChatGrpc
import com.codeinc.gen.chat.v2.ChatService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import javax.inject.Inject

class ChatApiV2 @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel)

    fun streamChatEvents(
        observer: StreamObserver<ChatService.StreamChatEventsResponse>
    ): StreamObserver<ChatService.StreamChatEventsRequest>? {
        return api.streamChatEvents(observer)
    }
}