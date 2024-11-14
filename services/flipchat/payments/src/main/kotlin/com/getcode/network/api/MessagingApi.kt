package com.getcode.network.api

import com.codeinc.gen.messaging.v1.MessagingGrpc
import com.codeinc.gen.messaging.v1.CodeMessagingService.AckMessagesRequest
import com.codeinc.gen.messaging.v1.CodeMessagingService.AckMesssagesResponse
import com.codeinc.gen.messaging.v1.CodeMessagingService.OpenMessageStreamRequest
import com.codeinc.gen.messaging.v1.CodeMessagingService.OpenMessageStreamResponse
import com.codeinc.gen.messaging.v1.CodeMessagingService.PollMessagesRequest
import com.codeinc.gen.messaging.v1.CodeMessagingService.SendMessageRequest
import com.codeinc.gen.messaging.v1.CodeMessagingService.SendMessageResponse
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import javax.inject.Inject

class MessagingApi @Inject constructor(
    @PaymentsManagedChannel
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
