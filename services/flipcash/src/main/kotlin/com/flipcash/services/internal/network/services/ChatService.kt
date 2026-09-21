package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.network.api.ChatApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetDmChatFeedError
import com.flipcash.services.models.GetGroupChatFeedError
import com.flipcash.services.models.GetRosterError
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.MuteChatError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.StartChatError
import com.flipcash.services.models.UnmuteChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewMode
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
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<ChatModel.Metadata> {
        return runCatching {
            api.getChat(owner, chatId, viewMode)
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

    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<RpcChatService.GetGroupChatFeedResponse> {
        return runCatching {
            api.getGroupChatFeed(owner, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetGroupChatFeedResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetGroupChatFeedResponse.Result.DENIED -> Result.failure(GetGroupChatFeedError.Denied())
                    RpcChatService.GetGroupChatFeedResponse.Result.NOT_FOUND -> Result.failure(GetGroupChatFeedError.NotFound())
                    RpcChatService.GetGroupChatFeedResponse.Result.UNRECOGNIZED -> Result.failure(GetGroupChatFeedError.Unrecognized())
                    else -> Result.failure(GetGroupChatFeedError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetGroupChatFeedError.Other(cause = it) })
            }
        )
    }

    suspend fun getRoster(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<RpcChatService.GetRosterResponse> {
        return runCatching {
            api.getRoster(owner, chatId, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetRosterResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetRosterResponse.Result.DENIED -> Result.failure(GetRosterError.Denied())
                    RpcChatService.GetRosterResponse.Result.NOT_FOUND -> Result.failure(GetRosterError.NotFound())
                    RpcChatService.GetRosterResponse.Result.UNRECOGNIZED -> Result.failure(GetRosterError.Unrecognized())
                    else -> Result.failure(GetRosterError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetRosterError.Other(cause = it) })
            }
        )
    }

    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatModel.Metadata> {
        return runCatching {
            api.joinChat(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.JoinChatResponse.Result.OK -> Result.success(response.chat)
                    RpcChatService.JoinChatResponse.Result.DENIED -> Result.failure(JoinChatError.Denied())
                    RpcChatService.JoinChatResponse.Result.NOT_FOUND -> Result.failure(JoinChatError.NotFound())
                    RpcChatService.JoinChatResponse.Result.RULES_NOT_SATISFIED -> Result.failure(JoinChatError.RulesNotSatisfied())
                    RpcChatService.JoinChatResponse.Result.UNRECOGNIZED -> Result.failure(JoinChatError.Unrecognized())
                    else -> Result.failure(JoinChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { JoinChatError.Other(cause = it) })
            }
        )
    }

    suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatModel.Metadata> {
        return runCatching {
            api.startChat(owner, parameters, idempotencyKey)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.StartChatResponse.Result.OK -> Result.success(response.chat)
                    RpcChatService.StartChatResponse.Result.DENIED -> Result.failure(StartChatError.Denied())
                    RpcChatService.StartChatResponse.Result.TITLE_MODERATED ->
                        Result.failure(StartChatError.TitleModerated(response.flaggedCategory.toFlaggedCategory()))
                    RpcChatService.StartChatResponse.Result.PICTURE_BLOB_NOT_ACCEPTED -> Result.failure(StartChatError.PictureBlobNotAccepted())
                    RpcChatService.StartChatResponse.Result.INVALID_RULES -> Result.failure(StartChatError.InvalidRules())
                    RpcChatService.StartChatResponse.Result.RULES_NOT_SATISFIED -> Result.failure(StartChatError.RulesNotSatisfied())
                    RpcChatService.StartChatResponse.Result.UNRECOGNIZED -> Result.failure(StartChatError.Unrecognized())
                    else -> Result.failure(StartChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { StartChatError.Other(cause = it) })
            }
        )
    }

    suspend fun editChat(
        owner: KeyPair,
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatModel.Metadata> {
        return runCatching {
            api.editChat(owner, chatId, parameters)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.EditChatResponse.Result.OK -> Result.success(response.chat)
                    RpcChatService.EditChatResponse.Result.DENIED -> Result.failure(EditChatError.Denied())
                    RpcChatService.EditChatResponse.Result.NOT_FOUND -> Result.failure(EditChatError.NotFound())
                    RpcChatService.EditChatResponse.Result.TITLE_MODERATED ->
                        Result.failure(EditChatError.TitleModerated(response.flaggedCategory.toFlaggedCategory()))
                    RpcChatService.EditChatResponse.Result.PICTURE_BLOB_NOT_ACCEPTED -> Result.failure(EditChatError.PictureBlobNotAccepted())
                    RpcChatService.EditChatResponse.Result.UNRECOGNIZED -> Result.failure(EditChatError.Unrecognized())
                    else -> Result.failure(EditChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { EditChatError.Other(cause = it) })
            }
        )
    }

    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit> {
        return runCatching {
            api.leaveChat(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.LeaveChatResponse.Result.OK -> Result.success(Unit)
                    RpcChatService.LeaveChatResponse.Result.DENIED -> Result.failure(LeaveChatError.Denied())
                    RpcChatService.LeaveChatResponse.Result.NOT_FOUND -> Result.failure(LeaveChatError.NotFound())
                    RpcChatService.LeaveChatResponse.Result.UNRECOGNIZED -> Result.failure(LeaveChatError.Unrecognized())
                    else -> Result.failure(LeaveChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { LeaveChatError.Other(cause = it) })
            }
        )
    }

    suspend fun muteChat(
        owner: KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): Result<ChatModel.ViewerState> {
        return runCatching {
            api.muteChat(owner, chatId, mute)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.MuteChatResponse.Result.OK -> Result.success(response.viewerState)
                    RpcChatService.MuteChatResponse.Result.DENIED -> Result.failure(MuteChatError.Denied())
                    RpcChatService.MuteChatResponse.Result.NOT_FOUND -> Result.failure(MuteChatError.NotFound())
                    RpcChatService.MuteChatResponse.Result.UNRECOGNIZED -> Result.failure(MuteChatError.Unrecognized())
                    else -> Result.failure(MuteChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { MuteChatError.Other(cause = it) })
            }
        )
    }

    suspend fun unmuteChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatModel.ViewerState> {
        return runCatching {
            api.unmuteChat(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.UnmuteChatResponse.Result.OK -> Result.success(response.viewerState)
                    RpcChatService.UnmuteChatResponse.Result.DENIED -> Result.failure(UnmuteChatError.Denied())
                    RpcChatService.UnmuteChatResponse.Result.NOT_FOUND -> Result.failure(UnmuteChatError.NotFound())
                    RpcChatService.UnmuteChatResponse.Result.UNRECOGNIZED -> Result.failure(UnmuteChatError.Unrecognized())
                    else -> Result.failure(UnmuteChatError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { UnmuteChatError.Other(cause = it) })
            }
        )
    }
}
