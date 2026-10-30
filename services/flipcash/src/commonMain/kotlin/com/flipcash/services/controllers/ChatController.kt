package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatterSample
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
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class ChatController @Inject constructor(
    private val repository: ChatRepository,
    private val userManager: UserManager,
) {
    suspend fun getChat(
        chatId: ChatId,
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getChat(owner, chatId, viewMode)
    }

    suspend fun getDmChatFeed(
        chatType: ChatType,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<ChatFeedPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getDmChatFeed(owner, queryOptions, chatType)
    }

    suspend fun getGroupChatFeed(
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<ChatFeedPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getGroupChatFeed(owner, queryOptions)
    }

    /**
     * Starts a new chat from [parameters].
     *
     * [idempotencyKey] is deliberately a required parameter rather than something this method
     * mints itself: for the retry safety to hold, the *caller* must mint it once where the
     * intent to start the chat originates (e.g. when the user taps "Create") and pass the same
     * instance again on every retry of that attempt. Minting a fresh key here would make every
     * retry look like a brand-new chat to the server.
     */
    suspend fun startChat(
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.startChat(owner, parameters, idempotencyKey)
    }

    /**
     * One page of [chatId]'s roster. See [ChatRepository.getRoster] for paging and merge
     * semantics.
     */
    suspend fun getRoster(
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
        inviteToken: InviteToken? = null,
    ): Result<RosterPage> {
        // With an invite token the account is optional: the token stands in for auth.
        val owner = userManager.accountCluster?.authority?.keyPair
        if (owner == null && inviteToken == null) {
            return Result.failure(Throwable("No account cluster in UserManager"))
        }

        return repository.getRoster(owner, chatId, queryOptions, inviteToken)
    }

    /**
     * A sample of [chatId]'s chatters; public groups only. Works without an account cluster, so a
     * preview viewer can call it -- the sample is the same either way.
     */
    suspend fun sampleChatters(chatId: ChatId): Result<ChatterSample> {
        val owner = userManager.accountCluster?.authority?.keyPair
        return repository.sampleChatters(owner, chatId)
    }

    /** Replaces the caller's featured groups. See [ChatRepository.setFeaturedGroups]. */
    suspend fun setFeaturedGroups(chatIds: List<ChatId>): Result<List<ChatMetadata>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.setFeaturedGroups(owner, chatIds)
    }

    /** [username]'s featured groups. See [ChatRepository.getFeaturedGroups]. */
    suspend fun getFeaturedGroups(username: String): Result<List<ChatMetadata>> {
        val owner = userManager.accountCluster?.authority?.keyPair
        return repository.getFeaturedGroups(owner, username)
    }

    /** The pool [chatId] offers for `@` mentions. See [ChatRepository.getMentionSuggestions]. */
    suspend fun getMentionSuggestions(chatId: ChatId): Result<List<MentionSuggestion>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getMentionSuggestions(owner, chatId)
    }

    /** Edits [chatId] per [parameters]. See [ChatRepository.editChat] for no-op semantics. */
    suspend fun editChat(
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.editChat(owner, chatId, parameters)
    }

    /** Joins [chatId], with [invite] when joining a private group through a link. */
    suspend fun joinChat(chatId: ChatId, invite: InviteParameters? = null): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.joinChat(owner, chatId, invite)
    }

    suspend fun leaveChat(chatId: ChatId): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.leaveChat(owner, chatId)
    }

    /** Mutes [chatId] for the caller per [mute], returning the caller's new viewer state. */
    suspend fun muteChat(chatId: ChatId, mute: MuteState): Result<ViewerState> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.muteChat(owner, chatId, mute)
    }

    /** Clears any mute on [chatId] for the caller, returning the caller's new viewer state. */
    suspend fun unmuteChat(chatId: ChatId): Result<ViewerState> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.unmuteChat(owner, chatId)
    }

    /** Enters private group [chatId]'s lobby. See [ChatRepository.enterLobby]. */
    suspend fun enterLobby(chatId: ChatId): Result<Lobby> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.enterLobby(owner, chatId)
    }

    /** Withdraws the caller from [chatId]'s lobby. */
    suspend fun leaveLobby(chatId: ChatId): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.leaveLobby(owner, chatId)
    }

    /** One page of [chatId]'s lobby. Creator only. */
    suspend fun getLobbyMembers(
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<LobbyPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getLobbyMembers(owner, chatId, queryOptions)
    }

    /** Admits [userId] from [chatId]'s lobby with the chat key wrapped for them in [keyEnvelope]. */
    suspend fun admitLobbyMember(
        chatId: ChatId,
        userId: ID,
        keyEnvelope: KeyEnvelope,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.admitLobbyMember(owner, chatId, userId, keyEnvelope)
    }

    /** Removes [userId] from [chatId]'s lobby without admitting them. */
    suspend fun denyLobbyMember(chatId: ChatId, userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.denyLobbyMember(owner, chatId, userId)
    }

    /** Silences [userId] in [chatId] for the duration in [silence]. */
    suspend fun silenceUser(chatId: ChatId, userId: ID, silence: SilenceState): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.silenceUser(owner, chatId, userId, silence)
    }

    /** Lifts [userId]'s silence in [chatId]. */
    suspend fun unsilenceUser(chatId: ChatId, userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.unsilenceUser(owner, chatId, userId)
    }

    /** Bans [userId] from [chatId]. */
    suspend fun banUser(chatId: ChatId, userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.banUser(owner, chatId, userId)
    }

    /** Lifts [userId]'s ban from [chatId]. */
    suspend fun unbanUser(chatId: ChatId, userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.unbanUser(owner, chatId, userId)
    }

    /** One page of the users silenced or banned in [chatId]. */
    suspend fun getRestrictedUsers(chatId: ChatId, queryOptions: QueryOptions = QueryOptions()): Result<RestrictedUsersPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getRestrictedUsers(owner, chatId, queryOptions)
    }

    /** Stores the caller's own key envelope for [chatId]. */
    suspend fun setKeyEnvelope(chatId: ChatId, keyEnvelope: KeyEnvelope): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.setKeyEnvelope(owner, chatId, keyEnvelope)
    }

    /** The caller's own key envelope for [chatId]. */
    suspend fun getKeyEnvelope(chatId: ChatId): Result<StoredKeyEnvelope> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getKeyEnvelope(owner, chatId)
    }

    /** Creates an invite link for private group [chatId]; see [ChatRepository.createInvite]. */
    suspend fun createInvite(
        chatId: ChatId,
        token: InviteToken,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: InviteSecretEnvelope,
        maxUses: Int = 0,
        expiresAt: Instant? = null,
    ): Result<Invite> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.createInvite(owner, chatId, token, keyEnvelope, secretEnvelope, maxUses, expiresAt)
    }

    /** What [token]'s link leads to. Works without an account cluster: an anonymous viewer can look an invite up. */
    suspend fun getInvite(token: InviteToken): Result<InvitePreview> {
        val owner = userManager.accountCluster?.authority?.keyPair
        return repository.getInvite(owner, token)
    }

    /** One page of [chatId]'s invites. */
    suspend fun getChatInvites(chatId: ChatId, queryOptions: QueryOptions = QueryOptions()): Result<InvitesPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getChatInvites(owner, chatId, queryOptions)
    }

    /** Revokes [inviteId] on [chatId]. */
    suspend fun revokeInvite(chatId: ChatId, inviteId: InviteId): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.revokeInvite(owner, chatId, inviteId)
    }
}
