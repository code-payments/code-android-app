package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.chat.v1.ChatGrpcKt
import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asProtoChatType
import com.flipcash.services.internal.network.extensions.asProtoIdempotencyKey
import com.flipcash.services.internal.network.extensions.asProtoRules
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.StartChatParameters
import com.getcode.utils.toByteString
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChatApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = ChatGrpcKt.ChatCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.GetChatResponse {
        val request = RpcChatService.GetChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getChat(request)
        }
    }

    suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): RpcChatService.GetDmChatFeedResponse {
        val request = RpcChatService.GetDmChatFeedRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .setDmChatType(chatType.asProtoChatType())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getDmChatFeed(request)
        }
    }

    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): RpcChatService.GetGroupChatFeedResponse {
        val request = RpcChatService.GetGroupChatFeedRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getGroupChatFeed(request)
        }
    }

    /**
     * Starts a new chat. [idempotencyKey] must be minted by the caller — once per logical
     * attempt — and reused across retries of the same attempt; generating a fresh key per call
     * defeats the retry-safety the server offers.
     */
    suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): RpcChatService.StartChatResponse {
        val requestBuilder = RpcChatService.StartChatRequest.newBuilder()
        when (parameters) {
            is StartChatParameters.Group -> requestBuilder.setGroup(
                RpcChatService.StartChatRequest.GroupChatParameters.newBuilder()
                    .setTitle(parameters.title)
                    .apply {
                        parameters.picture?.let {
                            setPicture(
                                com.codeinc.flipcash.gen.blob.v1.Model.BlobId.newBuilder()
                                    .setValue(it.bytes.toByteString())
                            )
                        }
                    }
                    .apply { parameters.rules?.let { setRules(it.asProtoRules()) } }
            )
        }

        val request = requestBuilder
            .setIdempotencyKey(idempotencyKey.asProtoIdempotencyKey())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.startChat(request)
        }
    }

    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.JoinChatResponse {
        val request = RpcChatService.JoinChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.joinChat(request)
        }
    }

    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): RpcChatService.LeaveChatResponse {
        val request = RpcChatService.LeaveChatRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.leaveChat(request)
        }
    }
}
