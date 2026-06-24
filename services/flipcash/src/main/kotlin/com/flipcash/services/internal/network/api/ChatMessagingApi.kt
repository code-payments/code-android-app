package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.messaging.v1.MessagingGrpcKt
import com.codeinc.flipcash.gen.messaging.v1.MessagingService as RpcMessagingService
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.codeinc.flipcash.gen.messaging.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asClientMessageId
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.internal.network.extensions.asEmoji
import com.flipcash.services.internal.network.extensions.asMessageId
import com.flipcash.services.internal.network.extensions.asPointerType
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asTypingState
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChatMessagingApi @Inject constructor(
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

    fun getDelta(
        owner: KeyPair,
        chatId: ChatId,
        afterSequence: Long,
    ): Flow<RpcMessagingService.GetDeltaResponse> {
        val request = RpcMessagingService.GetDeltaRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setAfterSequence(afterSequence)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return api.getDelta(request).flowOn(Dispatchers.IO)
    }

    suspend fun editMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        content: List<MessageContent>,
        expectedEventSequence: Long,
    ): RpcMessagingService.EditMessageResponse {
        val request = RpcMessagingService.EditMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .addAllContent(content.map { it.asContent() })
            .setExpectedEventSequence(expectedEventSequence)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.editMessage(request)
        }
    }

    suspend fun deleteMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        expectedEventSequence: Long,
    ): RpcMessagingService.DeleteMessageResponse {
        val request = RpcMessagingService.DeleteMessageRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .setExpectedEventSequence(expectedEventSequence)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.deleteMessage(request)
        }
    }

    suspend fun addReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): RpcMessagingService.AddReactionResponse {
        val request = RpcMessagingService.AddReactionRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .setEmoji(emoji.asEmoji())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.addReaction(request)
        }
    }

    suspend fun removeReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): RpcMessagingService.RemoveReactionResponse {
        val request = RpcMessagingService.RemoveReactionRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .setEmoji(emoji.asEmoji())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.removeReaction(request)
        }
    }

    suspend fun getReactors(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
        queryOptions: QueryOptions,
    ): RpcMessagingService.GetReactorsResponse {
        val request = RpcMessagingService.GetReactorsRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .setEmoji(emoji.asEmoji())
            .setOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getReactors(request)
        }
    }

    suspend fun getReactionSummary(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): RpcMessagingService.GetReactionSummaryResponse {
        val request = RpcMessagingService.GetReactionSummaryRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setMessageId(messageId.asMessageId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getReactionSummary(request)
        }
    }

    suspend fun getReactionSummaries(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): RpcMessagingService.GetReactionSummariesResponse {
        val request = RpcMessagingService.GetReactionSummariesRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getReactionSummaries(request)
        }
    }
}
