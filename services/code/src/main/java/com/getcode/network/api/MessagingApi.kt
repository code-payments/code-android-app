package com.getcode.network.api

import com.codeinc.gen.messaging.v1.MessagingGrpc
import com.codeinc.gen.messaging.v1.MessagingService.AckMessagesRequest
import com.codeinc.gen.messaging.v1.MessagingService.AckMesssagesResponse
import com.codeinc.gen.messaging.v1.MessagingService.OpenMessageStreamRequest
import com.codeinc.gen.messaging.v1.MessagingService.OpenMessageStreamResponse
import com.codeinc.gen.messaging.v1.MessagingService.PollMessagesRequest
import com.codeinc.gen.messaging.v1.MessagingService.SendMessageRequest
import com.codeinc.gen.messaging.v1.MessagingService.SendMessageResponse
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class MessagingApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io()
) : GrpcApi(managedChannel) {
    private val api = MessagingGrpc.newStub(managedChannel).withWaitForReady()

    fun openMessageStream(request: OpenMessageStreamRequest): Flowable<OpenMessageStreamResponse> =
        api::openMessageStream
            .callAsCancellableFlowable(request)
            .subscribeOn(scheduler)

    fun ackMessages(request: AckMessagesRequest): Single<AckMesssagesResponse> =
        api::ackMessages
            .callAsSingle(request)
            .subscribeOn(scheduler)

    fun sendMessage(request: SendMessageRequest): Single<SendMessageResponse> =
        api::sendMessage
            .callAsSingle(request)
            .subscribeOn(scheduler)

    fun pollMessages(request: PollMessagesRequest) = api::pollMessages
        .callAsSingle(request)
        .subscribeOn(scheduler)
}
