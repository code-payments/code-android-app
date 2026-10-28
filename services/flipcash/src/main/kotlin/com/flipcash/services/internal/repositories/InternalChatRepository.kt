package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.domain.UserProfileMapper
import com.flipcash.services.internal.network.extensions.toChatMember
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toInvite
import com.flipcash.services.internal.network.extensions.toInviteKeyBundle
import com.flipcash.services.internal.network.extensions.toInviteStatus
import com.flipcash.services.internal.network.extensions.toKeyEnvelope
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toLobbyMember
import com.flipcash.services.internal.network.extensions.toRestrictedUser
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.extensions.toRosterSummary
import com.flipcash.services.internal.network.extensions.toViewerState
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.models.CreateInviteError
import com.flipcash.services.models.GetChatInvitesError
import com.flipcash.services.models.GetInviteError
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.GetKeyEnvelopeError
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.SampleChattersError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatterSample
import com.flipcash.services.models.chat.SampledChatter
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteKeyEnvelope
import com.flipcash.services.models.chat.InviteParameters
import com.flipcash.services.models.chat.InvitePreview
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.services.models.chat.InvitesPage
import com.flipcash.services.models.chat.KeyEnvelope
import com.flipcash.services.models.chat.Lobby
import com.flipcash.services.models.chat.LobbyPage
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RestrictedUsersPage
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.services.models.chat.MentionSuggestion
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.StoredKeyEnvelope
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.repository.ChatRepository
import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils
import kotlin.time.Instant

internal class InternalChatRepository(
    private val service: ChatService,
    private val mapper: ChatMetadataMapper,
    private val userProfileMapper: UserProfileMapper,
) : ChatRepository {
    override suspend fun sampleChatters(
        owner: KeyPair?,
        chatId: ChatId,
    ): Result<ChatterSample> = service.sampleChatters(owner, chatId)
        .onFailure { if (it !is SampleChattersError.Denied) ErrorUtils.handleError(it) }
        .map { response ->
            ChatterSample(
                chatters = response.chattersList.map { chatter ->
                    SampledChatter(
                        userProfile = userProfileMapper.map(chatter.userProfile),
                        lastSentAt = if (chatter.hasLastSentAt()) {
                            Instant.fromEpochSeconds(chatter.lastSentAt.seconds, chatter.lastSentAt.nanos)
                        } else null,
                        isCreator = chatter.isCreator,
                    )
                },
                hasMore = response.hasMore,
            )
        }

    override suspend fun setFeaturedGroups(
        owner: KeyPair,
        chatIds: List<ChatId>,
    ): Result<List<ChatMetadata>> = service.setFeaturedGroups(owner, chatIds)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response -> response.featuredGroupsList.map { mapper.map(it) } }

    override suspend fun getFeaturedGroups(
        owner: KeyPair?,
        username: String,
    ): Result<List<ChatMetadata>> = service.getFeaturedGroups(owner, username)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response -> response.featuredGroupsList.map { mapper.map(it) } }

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
        owner: KeyPair?,
        chatId: ChatId,
        queryOptions: QueryOptions,
        inviteToken: InviteToken?,
    ): Result<RosterPage> = service.getRoster(owner, chatId, queryOptions, inviteToken)
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
        invite: InviteParameters?,
    ): Result<ChatMetadata> = service.joinChat(owner, chatId, invite)
        .onFailure { if (!it.isExpectedInviteFailure()) ErrorUtils.handleError(it) }
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

    override suspend fun silenceUser(
        owner: KeyPair,
        chatId: ChatId,
        userId: ID,
        silence: SilenceState,
    ): Result<Unit> = service.silenceUser(owner, chatId, userId, silence)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun unsilenceUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit> =
        service.unsilenceUser(owner, chatId, userId)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun banUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit> =
        service.banUser(owner, chatId, userId)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun unbanUser(owner: KeyPair, chatId: ChatId, userId: ID): Result<Unit> =
        service.unbanUser(owner, chatId, userId)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getRestrictedUsers(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<RestrictedUsersPage> = service.getRestrictedUsers(owner, chatId, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            RestrictedUsersPage(
                users = response.usersList.map { it.toRestrictedUser() },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun setKeyEnvelope(
        owner: KeyPair,
        chatId: ChatId,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> = service.setKeyEnvelope(owner, chatId, keyEnvelope)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getKeyEnvelope(owner: KeyPair, chatId: ChatId): Result<StoredKeyEnvelope> =
        service.getKeyEnvelope(owner, chatId)
            .onFailure { ErrorUtils.handleError(it) }
            .mapCatching { response ->
                // keyEnvelope is a oneof member: reading it while invite_key_bundle is set yields a
                // default (empty) envelope, so branch on the case before touching it.
                when (response.envelopeCase) {
                    RpcChatService.GetKeyEnvelopeResponse.EnvelopeCase.KEY_ENVELOPE -> StoredKeyEnvelope(
                        envelope = response.keyEnvelope.toKeyEnvelope(),
                        wrappedBy = if (response.hasWrappedBy()) response.wrappedBy.toId() else null,
                        wrappedByPublicKey = if (response.hasWrappedByPublicKey()) response.wrappedByPublicKey.toPublicKey() else null,
                    )
                    RpcChatService.GetKeyEnvelopeResponse.EnvelopeCase.INVITE_KEY_BUNDLE ->
                        throw GetKeyEnvelopeError.InviteKeyBundleOnly(response.toInviteKeyBundle())
                    // An OK with nothing stored, or a case this build does not know: no usable own envelope.
                    else -> throw GetKeyEnvelopeError.NoEnvelope()
                }
            }

    override suspend fun createInvite(
        owner: KeyPair,
        chatId: ChatId,
        token: InviteToken,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: InviteSecretEnvelope,
        maxUses: Int,
        expiresAt: Instant?,
    ): Result<Invite> = service.createInvite(owner, chatId, token, keyEnvelope, secretEnvelope, maxUses, expiresAt)
        .onFailure { if (!it.isExpectedInviteFailure()) ErrorUtils.handleError(it) }
        .map { it.toInvite() }

    override suspend fun getInvite(owner: KeyPair?, token: InviteToken): Result<InvitePreview> =
        service.getInvite(owner, token)
            .onFailure { if (!it.isExpectedInviteFailure()) ErrorUtils.handleError(it) }
            .map { response ->
                InvitePreview(
                    chat = mapper.map(response.chat),
                    status = response.status.toInviteStatus(),
                )
            }

    override suspend fun getChatInvites(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<InvitesPage> = service.getChatInvites(owner, chatId, queryOptions)
        .onFailure { if (!it.isExpectedInviteFailure()) ErrorUtils.handleError(it) }
        .map { response ->
            InvitesPage(
                invites = response.invitesList.map { it.toInvite() },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun revokeInvite(owner: KeyPair, chatId: ChatId, inviteId: InviteId): Result<Unit> =
        service.revokeInvite(owner, chatId, inviteId)
            .onFailure { if (!it.isExpectedInviteFailure()) ErrorUtils.handleError(it) }

}

// Expected outcomes of a stale or bad link; not worth reporting. JoinChat's three dead-invite
// results are here too: the joiner sees them as the dead-link state, not as an error.
internal fun Throwable.isExpectedInviteFailure(): Boolean =
        this is JoinChatError.InviteExhausted || this is JoinChatError.InviteExpired ||
            this is JoinChatError.InviteRevoked ||
            this is CreateInviteError.Denied || this is CreateInviteError.NotFound ||
            this is CreateInviteError.InvalidExpiresAt || this is CreateInviteError.TooManyInvites ||
            this is GetInviteError.NotFound || this is GetInviteError.Banned ||
            this is GetChatInvitesError.Denied || this is GetChatInvitesError.NotFound ||
            this is RevokeInviteError.Denied || this is RevokeInviteError.NotFound
