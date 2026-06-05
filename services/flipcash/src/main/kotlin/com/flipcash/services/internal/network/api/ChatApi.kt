package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.chat.v1.ChatGrpcKt
import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
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
    ): RpcChatService.GetDmChatFeedResponse {
        val request = RpcChatService.GetDmChatFeedRequest.newBuilder()
            .setQueryOptions(queryOptions.asQueryOptions())
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getDmChatFeed(request)
        }
    }
}
