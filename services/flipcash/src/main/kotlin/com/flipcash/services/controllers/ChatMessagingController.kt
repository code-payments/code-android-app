package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.repository.ChatMessagingRepository
import com.flipcash.services.repository.DeltaUpdate
import com.flipcash.services.repository.ReactorsPage
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessagingController @Inject constructor(
    private val repository: ChatMessagingRepository,
    private val userManager: UserManager,
) {
    private fun requireOwner() = userManager.accountCluster?.authority?.keyPair
        ?: throw IllegalStateException("No account cluster in UserManager")

    suspend fun getMessage(
        chatId: ChatId,
        messageId: Long,
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessage(owner, chatId, messageId, viewMode)
    }

    suspend fun getMessages(
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<List<ChatMessage>> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessages(owner, chatId, queryOptions, viewMode)
    }

    suspend fun getMessagesByIds(
        chatId: ChatId,
        messageIds: List<Long>,
        viewMode: ViewMode = ViewMode.FULL,
    ): Result<List<ChatMessage>> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessagesByIds(owner, chatId, messageIds, viewMode)
    }

    fun getDelta(
        chatId: ChatId,
        afterSequence: Long,
        viewMode: ViewMode = ViewMode.FULL,
    ): Flow<Result<DeltaUpdate>> {
        val owner = requireOwner()
        return repository.getDelta(owner, chatId, afterSequence, viewMode)
    }

    suspend fun sendMessage(
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.sendMessage(owner, chatId, content, clientMessageId)
    }

    suspend fun editMessage(
        chatId: ChatId,
        messageId: Long,
        content: List<MessageContent>,
        expectedEventSequence: Long,
    ): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.editMessage(owner, chatId, messageId, content, expectedEventSequence)
    }

    suspend fun deleteMessage(
        chatId: ChatId,
        messageId: Long,
        expectedEventSequence: Long,
    ): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.deleteMessage(owner, chatId, messageId, expectedEventSequence)
    }

    suspend fun addReaction(
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.addReaction(owner, chatId, messageId, emoji)
    }

    suspend fun removeReaction(
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.removeReaction(owner, chatId, messageId, emoji)
    }

    /** [queryOptions]`.limit` (page size) is clamped at [REACTORS_PAGE_SIZE_LIMIT], the server cap. */
    suspend fun getReactors(
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<ReactorsPage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        val clamped = queryOptions.copy(limit = queryOptions.limit.coerceAtMost(REACTORS_PAGE_SIZE_LIMIT))
        return repository.getReactors(owner, chatId, messageId, emoji, clamped)
    }

    suspend fun getReactionSummary(
        chatId: ChatId,
        messageId: Long,
    ): Result<ReactionSummary> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getReactionSummary(owner, chatId, messageId)
    }

    suspend fun getReactionSummaries(
        chatId: ChatId,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<List<ReactionSummary>> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getReactionSummaries(owner, chatId, queryOptions)
    }

    /**
     * Fetches reaction summaries for [messageIds], chunking into batches of at most
     * [MESSAGE_ID_BATCH_LIMIT] (the server's `MessageIdBatch` cap) and merging the results.
     * Fails fast on the first chunk that fails; earlier chunks already fetched are discarded
     * with it since a partial summary set isn't safe to seed reaction state from.
     */
    suspend fun getReactionSummariesByIds(
        chatId: ChatId,
        messageIds: List<Long>,
    ): Result<List<ReactionSummary>> {
        if (messageIds.isEmpty()) return Result.success(emptyList())
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        val merged = mutableListOf<ReactionSummary>()
        for (chunk in messageIds.chunked(MESSAGE_ID_BATCH_LIMIT)) {
            val result = repository.getReactionSummariesByIds(owner, chatId, chunk)
            val summaries = result.getOrElse { return Result.failure(it) }
            merged += summaries
        }
        return Result.success(merged)
    }

    companion object {
        private const val MESSAGE_ID_BATCH_LIMIT = 100
        private const val REACTORS_PAGE_SIZE_LIMIT = 100
    }

    suspend fun advancePointer(
        chatId: ChatId,
        pointerType: PointerType,
        messageId: Long,
    ): Result<Unit> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.advancePointer(owner, chatId, pointerType, messageId)
    }

    suspend fun notifyIsTyping(
        chatId: ChatId,
        state: TypingState,
    ): Result<Unit> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.notifyIsTyping(owner, chatId, state)
    }
}
