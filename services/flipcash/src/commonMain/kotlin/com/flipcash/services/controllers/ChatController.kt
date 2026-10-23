package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatterSample
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
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject
import javax.inject.Singleton

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
    ): Result<RosterPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getRoster(owner, chatId, queryOptions)
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

    suspend fun joinChat(chatId: ChatId): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.joinChat(owner, chatId)
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
}
