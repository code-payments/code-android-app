package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.network.api.ChatApi
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetDmChatFeedError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class ChatService @Inject constructor(
    private val api: ChatApi,
) {
    suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatModel.Metadata> {
        return runCatching {
            api.getChat(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetChatResponse.Result.OK -> Result.success(response.metadata)
                    RpcChatService.GetChatResponse.Result.DENIED -> Result.failure(GetChatError.Denied())
                    RpcChatService.GetChatResponse.Result.NOT_FOUND -> Result.failure(GetChatError.NotFound())
                    RpcChatService.GetChatResponse.Result.UNRECOGNIZED -> Result.failure(GetChatError.Unrecognized())
                    else -> Result.failure(GetChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetChatError.Other(cause = it) })
            }
        )
    }

    suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<RpcChatService.GetDmChatFeedResponse> {
        return runCatching {
            api.getDmChatFeed(owner, queryOptions, chatType)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetDmChatFeedResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetDmChatFeedResponse.Result.DENIED -> Result.failure(GetDmChatFeedError.Denied())
                    RpcChatService.GetDmChatFeedResponse.Result.NOT_FOUND -> Result.failure(GetDmChatFeedError.NotFound())
                    RpcChatService.GetDmChatFeedResponse.Result.UNRECOGNIZED -> Result.failure(GetDmChatFeedError.Unrecognized())
                    else -> Result.failure(GetDmChatFeedError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetDmChatFeedError.Other(cause = it) })
            }
        )
    }
}
