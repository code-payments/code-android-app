package com.getcode.oct24.internal.network.api

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.codeinc.flipchat.gen.messaging.v1.MessagingGrpc
import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.codeinc.flipchat.gen.messaging.v1.Model.Pointer
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.OutgoingMessageContent
import com.getcode.oct24.annotations.FcManagedChannel
import com.getcode.oct24.internal.network.core.GrpcApi
import com.getcode.oct24.internal.network.extensions.forAuth
import com.getcode.oct24.internal.network.extensions.toChatId
import com.getcode.oct24.internal.network.extensions.toPagingToken
import com.getcode.oct24.internal.network.extensions.toProto
import com.getcode.oct24.model.query.QueryOptions
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MessagingApi @Inject constructor(
    @FcManagedChannel
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = MessagingGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * gets the set of messages for a chat member using a paged API
     */
    fun getMessages(
        owner: KeyPair,
        chatId: ID,
        queryOptions: QueryOptions,
    ): Flow<MessagingService.GetMessagesResponse> {
        val request = MessagingService.GetMessagesRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setQueryOptions(queryOptions.toProto())
            .setAuth(owner.forAuth())
            .build()

        return api::getMessages
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * advances a pointer in message history for a chat member.
     */
    fun advancePointer(
        owner: KeyPair,
        chatId: ID,
        to: ID,
        status: MessageStatus,
    ): Flow<MessagingService.AdvancePointerResponse> {
        val request = MessagingService.AdvancePointerRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setPointer(
                Pointer.newBuilder()
                    .setValue(Model.MessageId.newBuilder().setValue(to.toByteString()))
                    .setType(
                        when (status) {
                            MessageStatus.Sent -> Model.Pointer.Type.SENT
                            MessageStatus.Delivered -> Model.Pointer.Type.DELIVERED
                            MessageStatus.Read -> Model.Pointer.Type.READ
                            MessageStatus.Unknown -> Model.Pointer.Type.UNKNOWN
                        }
                    )
            )
            .setAuth(owner.forAuth())
            .build()

        return api::advancePointer
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * sends a message to a chat.
     */
    fun sendMessage(
        owner: KeyPair,
        chatId: ID,
        content: OutgoingMessageContent,
        observer: StreamObserver<MessagingService.SendMessageResponse>
    ) {
        val contentProto = when (content) {
            is OutgoingMessageContent.Text -> Model.Content.newBuilder()
                .setText(Model.TextContent.newBuilder().setText(content.text))

            is OutgoingMessageContent.Encrypted -> TODO()
            is OutgoingMessageContent.LocalizedText -> TODO()
        }

        val request = MessagingService.SendMessageRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .addContent(contentProto)
            .setAuth(owner.forAuth())
            .build()

        api.sendMessage(request, observer)
    }

    fun notifyIsTyping(
        owner: KeyPair,
        chatId: ID,
        isTyping: Boolean,
        observer: StreamObserver<MessagingService.NotifyIsTypingResponse>
    ) {
        val request = MessagingService.NotifyIsTypingRequest.newBuilder()
            .setChatId(chatId.toChatId())
            .setIsTyping(isTyping)
            .setAuth(owner.forAuth())
            .build()

        api.notifyIsTyping(request, observer)
    }

    fun streamMessages(
        observer: StreamObserver<MessagingService.StreamMessagesResponse>
    ): StreamObserver<MessagingService.StreamMessagesRequest>? {
        return api.streamMessages(observer)
    }

}