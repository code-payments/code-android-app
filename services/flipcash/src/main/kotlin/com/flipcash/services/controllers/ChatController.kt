package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.RosterPage
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.repository.ChatRepository
import com.flipcash.services.user.UserManager
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
}
