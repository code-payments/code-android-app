package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.messaging.v1.MessagingGrpcKt
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.codeinc.flipcash.gen.messaging.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asClientMessageId
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.asPointerType
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asTypingState
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MessagingApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = MessagingGrpcKt.MessagingCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): RpcMessagingService.GetMessageResponse {
        val request = RpcMessagingService.GetMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(MessagingModel.MessageId.newBuilder().setValue(messageId))
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getMessage(request)
        }
    }

    suspend fun getMessages(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): RpcMessagingService.GetMessagesResponse {
        val request = RpcMessagingService.GetMessagesRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getMessages(request)
        }
    }

    suspend fun getMessagesByIds(
        owner: KeyPair,
        chatId: ChatId,
        messageIds: List<Long>,
    ): RpcMessagingService.GetMessagesResponse {
        val request = RpcMessagingService.GetMessagesRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageIds(
                MessagingModel.MessageIdBatch.newBuilder()
                    .addAllMessageIds(messageIds.map { MessagingModel.MessageId.newBuilder().setValue(it).build() })
            )
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getMessages(request)
        }
    }

    suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): RpcMessagingService.SendMessageResponse {
        val request = RpcMessagingService.SendMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .addAllContent(content.map { it.asContent() })
            .setClientMessageId(clientMessageId.asClientMessageId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.sendMessage(request)
        }
    }

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ChatId,
        pointerType: PointerType,
        messageId: Long,
    ): RpcMessagingService.AdvancePointerResponse {
        val request = RpcMessagingService.AdvancePointerRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setPointerType(pointerType.asPointerType())
            .setNewValue(MessagingModel.MessageId.newBuilder().setValue(messageId))
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.advancePointer(request)
        }
    }

    suspend fun notifyIsTyping(
        owner: KeyPair,
        chatId: ChatId,
        state: TypingState,
    ): RpcMessagingService.NotifyIsTypingResponse {
        val request = RpcMessagingService.NotifyIsTypingRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setState(state.asTypingState())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.notifyIsTyping(request)
        }
    }
}
