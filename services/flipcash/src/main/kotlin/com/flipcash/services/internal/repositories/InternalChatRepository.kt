package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.network.extensions.toChatMember
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toKeyEnvelope
import com.flipcash.services.internal.network.extensions.toLobbyMember
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.extensions.toRosterSummary
import com.flipcash.services.internal.network.extensions.toViewerState
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.Lobby
import com.flipcash.services.models.chat.LobbyPage
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.MentionSuggestion
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.repository.ChatRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils
import kotlin.time.Instant

internal class InternalChatRepository(
    private val service: ChatService,
    private val mapper: ChatMetadataMapper,
    private val userProfileMapper: UserProfileMapper,
) : ChatRepository {
    override suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
        viewMode: ViewMode,
    ): Result<ChatMetadata> = service.getChat(owner, chatId, viewMode)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<ChatFeedPage> = service.getDmChatFeed(owner, queryOptions, chatType)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            ChatFeedPage(
                chats = response.chatsList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage> = service.getGroupChatFeed(owner, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            ChatFeedPage(
                chats = response.chatsList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata> = service.startChat(owner, parameters, idempotencyKey)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun getRoster(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<RosterPage> = service.getRoster(owner, chatId, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            RosterPage(
                members = response.membersList.map { it.toChatMember() },
                rosterSummary = response.rosterSummary.toRosterSummary(),
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun getMentionSuggestions(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<List<MentionSuggestion>> = service.getMentionSuggestions(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            response.suggestionsList.map { suggestion ->
                MentionSuggestion(
                    userProfile = userProfileMapper.map(suggestion.userProfile),
                    lastSentAt = if (suggestion.hasLastSentAt()) {
                        Instant.fromEpochSeconds(suggestion.lastSentAt.seconds, suggestion.lastSentAt.nanos)
                    } else null,
                )
            }
        }

    override suspend fun editChat(
        owner: KeyPair,
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata> = service.editChat(owner, chatId, parameters)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata> = service.joinChat(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit> = service.leaveChat(owner, chatId)
        // Recovered before reporting, so an already-left chat is not logged as a failure.
        .recoverCatching { cause -> if (cause is LeaveChatError.NotFound) Unit else throw cause }
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun muteChat(
        owner: KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): Result<ViewerState> = service.muteChat(owner, chatId, mute)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toViewerState() }

    override suspend fun unmuteChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ViewerState> = service.unmuteChat(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toViewerState() }

    override suspend fun enterLobby(owner: KeyPair, chatId: ChatId): Result<Lobby> =
        service.enterLobby(owner, chatId)
            .onFailure { ErrorUtils.handleError(it) }
            .map { lobby ->
                Lobby(
                    chat = mapper.map(lobby.chat),
                    enteredAt = Instant.fromEpochSeconds(lobby.enteredAt.seconds, lobby.enteredAt.nanos),
                )
            }

    override suspend fun leaveLobby(owner: KeyPair, chatId: ChatId): Result<Unit> =
        service.leaveLobby(owner, chatId)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getLobbyMembers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<LobbyPage> = service.getLobbyMembers(owner, chatId, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            LobbyPage(
                members = response.membersList.map { it.toLobbyMember() },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun admitLobbyMember(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> = service.admitLobbyMember(owner, chatId, userId, keyEnvelope)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun denyLobbyMember(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit> =
        service.denyLobbyMember(owner, chatId, userId)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun setKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> = service.setKeyEnvelope(owner, chatId, keyEnvelope)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getKeyEnvelope(owner: KeyPair, chatId: ChatId): Result<StoredKeyEnvelope> =
        service.getKeyEnvelope(owner, chatId)
            .onFailure { ErrorUtils.handleError(it) }
            .map { response ->
                StoredKeyEnvelope(
                    envelope = response.keyEnvelope.toKeyEnvelope(),
                    wrappedBy = if (response.hasWrappedBy()) response.wrappedBy.toId() else null,
                )
            }
}
