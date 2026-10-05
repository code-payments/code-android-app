package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.network.api.ChatApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.flipcash.services.models.AdmitLobbyMemberError
import com.flipcash.services.models.DenyLobbyMemberError
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.EnterLobbyError
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.GetLobbyMembersError
import com.flipcash.services.models.LeaveLobbyError
import com.flipcash.services.models.SetKeyEnvelopeError
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.GetDmChatFeedError
import com.flipcash.services.models.GetGroupChatFeedError
import com.flipcash.services.models.GetMentionSuggestionsError
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
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewMode
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.ID
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

    suspend fun getMentionSuggestions(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<RpcChatService.GetMentionSuggestionsResponse> {
        return runCatching {
            api.getMentionSuggestions(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetMentionSuggestionsResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetMentionSuggestionsResponse.Result.DENIED -> Result.failure(GetMentionSuggestionsError.Denied())
                    RpcChatService.GetMentionSuggestionsResponse.Result.NOT_FOUND -> Result.failure(GetMentionSuggestionsError.NotFound())
                    RpcChatService.GetMentionSuggestionsResponse.Result.UNRECOGNIZED -> Result.failure(GetMentionSuggestionsError.Unrecognized())
                    else -> Result.failure(GetMentionSuggestionsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetMentionSuggestionsError.Other(cause = it) })
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

    suspend fun enterLobby(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatModel.Lobby> {
        return runCatching {
            api.enterLobby(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.EnterLobbyResponse.Result.OK -> Result.success(response.lobby)
                    RpcChatService.EnterLobbyResponse.Result.DENIED -> Result.failure(EnterLobbyError.Denied())
                    RpcChatService.EnterLobbyResponse.Result.NOT_FOUND -> Result.failure(EnterLobbyError.NotFound())
                    RpcChatService.EnterLobbyResponse.Result.ALREADY_MEMBER -> Result.failure(EnterLobbyError.AlreadyMember())
                    RpcChatService.EnterLobbyResponse.Result.LOBBY_FULL -> Result.failure(EnterLobbyError.LobbyFull())
                    RpcChatService.EnterLobbyResponse.Result.TOO_MANY_LOBBIES -> Result.failure(EnterLobbyError.TooManyLobbies())
                    RpcChatService.EnterLobbyResponse.Result.UNRECOGNIZED -> Result.failure(EnterLobbyError.Unrecognized())
                    else -> Result.failure(EnterLobbyError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { EnterLobbyError.Other(cause = it) })
            }
        )
    }

    suspend fun leaveLobby(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit> {
        return runCatching {
            api.leaveLobby(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.LeaveLobbyResponse.Result.OK -> Result.success(Unit)
                    RpcChatService.LeaveLobbyResponse.Result.DENIED -> Result.failure(LeaveLobbyError.Denied())
                    RpcChatService.LeaveLobbyResponse.Result.NOT_FOUND -> Result.failure(LeaveLobbyError.NotFound())
                    RpcChatService.LeaveLobbyResponse.Result.UNRECOGNIZED -> Result.failure(LeaveLobbyError.Unrecognized())
                    else -> Result.failure(LeaveLobbyError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { LeaveLobbyError.Other(cause = it) })
            }
        )
    }

    suspend fun getLobbyMembers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<RpcChatService.GetLobbyMembersResponse> {
        return runCatching {
            api.getLobbyMembers(owner, chatId, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetLobbyMembersResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetLobbyMembersResponse.Result.DENIED -> Result.failure(GetLobbyMembersError.Denied())
                    RpcChatService.GetLobbyMembersResponse.Result.NOT_FOUND -> Result.failure(GetLobbyMembersError.NotFound())
                    RpcChatService.GetLobbyMembersResponse.Result.UNRECOGNIZED -> Result.failure(GetLobbyMembersError.Unrecognized())
                    else -> Result.failure(GetLobbyMembersError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetLobbyMembersError.Other(cause = it) })
            }
        )
    }

    suspend fun admitLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> {
        return runCatching {
            api.admitLobbyMember(owner, chatId, userId, keyEnvelope)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.AdmitLobbyMemberResponse.Result.OK -> Result.success(Unit)
                    RpcChatService.AdmitLobbyMemberResponse.Result.DENIED -> Result.failure(AdmitLobbyMemberError.Denied())
                    RpcChatService.AdmitLobbyMemberResponse.Result.NOT_FOUND -> Result.failure(AdmitLobbyMemberError.NotFound())
                    RpcChatService.AdmitLobbyMemberResponse.Result.NOT_IN_LOBBY -> Result.failure(AdmitLobbyMemberError.NotInLobby())
                    RpcChatService.AdmitLobbyMemberResponse.Result.UNRECOGNIZED -> Result.failure(AdmitLobbyMemberError.Unrecognized())
                    else -> Result.failure(AdmitLobbyMemberError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { AdmitLobbyMemberError.Other(cause = it) })
            }
        )
    }

    suspend fun denyLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
    ): Result<Unit> {
        return runCatching {
            api.denyLobbyMember(owner, chatId, userId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.DenyLobbyMemberResponse.Result.OK -> Result.success(Unit)
                    RpcChatService.DenyLobbyMemberResponse.Result.DENIED -> Result.failure(DenyLobbyMemberError.Denied())
                    RpcChatService.DenyLobbyMemberResponse.Result.NOT_FOUND -> Result.failure(DenyLobbyMemberError.NotFound())
                    RpcChatService.DenyLobbyMemberResponse.Result.UNRECOGNIZED -> Result.failure(DenyLobbyMemberError.Unrecognized())
                    else -> Result.failure(DenyLobbyMemberError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { DenyLobbyMemberError.Other(cause = it) })
            }
        )
    }

    suspend fun setKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> {
        return runCatching {
            api.setKeyEnvelope(owner, chatId, keyEnvelope)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.SetKeyEnvelopeResponse.Result.OK -> Result.success(Unit)
                    RpcChatService.SetKeyEnvelopeResponse.Result.DENIED -> Result.failure(SetKeyEnvelopeError.Denied())
                    RpcChatService.SetKeyEnvelopeResponse.Result.NOT_FOUND -> Result.failure(SetKeyEnvelopeError.NotFound())
                    RpcChatService.SetKeyEnvelopeResponse.Result.ALREADY_SET -> Result.failure(SetKeyEnvelopeError.AlreadySet())
                    RpcChatService.SetKeyEnvelopeResponse.Result.UNRECOGNIZED -> Result.failure(SetKeyEnvelopeError.Unrecognized())
                    else -> Result.failure(SetKeyEnvelopeError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { SetKeyEnvelopeError.Other(cause = it) })
            }
        )
    }

    suspend fun getKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<RpcChatService.GetKeyEnvelopeResponse> {
        return runCatching {
            api.getKeyEnvelope(owner, chatId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcChatService.GetKeyEnvelopeResponse.Result.OK -> Result.success(response)
                    RpcChatService.GetKeyEnvelopeResponse.Result.DENIED -> Result.failure(GetKeyEnvelopeError.Denied())
                    RpcChatService.GetKeyEnvelopeResponse.Result.NOT_FOUND -> Result.failure(GetKeyEnvelopeError.NotFound())
                    RpcChatService.GetKeyEnvelopeResponse.Result.NO_ENVELOPE -> Result.failure(GetKeyEnvelopeError.NoEnvelope())
                    RpcChatService.GetKeyEnvelopeResponse.Result.UNRECOGNIZED -> Result.failure(GetKeyEnvelopeError.Unrecognized())
                    else -> Result.failure(GetKeyEnvelopeError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetKeyEnvelopeError.Other(cause = it) })
            }
        )
    }
}
