package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.extensions.toChatMessage
import com.flipcash.services.internal.network.services.ChatMessagingService
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
import com.flipcash.services.repository.ChatMessagingRepository
import com.flipcash.services.repository.DeltaUpdate
import com.flipcash.services.repository.ReactorsPage
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class InternalChatMessagingRepository(
    private val service: ChatMessagingService,
) : ChatMessagingRepository {

    override suspend fun getMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<ChatMessage> = service.getMessage(owner, chatId, messageId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toChatMessage() }

    override suspend fun getMessages(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<ChatMessage>> = service.getMessages(owner, chatId, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { messages -> messages.map { it.toChatMessage() } }

    override suspend fun getMessagesByIds(
        owner: KeyPair,
        chatId: ChatId,
        messageIds: List<Long>,
    ): Result<List<ChatMessage>> = service.getMessagesByIds(owner, chatId, messageIds)
        .onFailure { ErrorUtils.handleError(it) }
        .map { messages -> messages.map { it.toChatMessage() } }

    override fun getDelta(
        owner: KeyPair,
        chatId: ChatId,
        afterSequence: Long,
    ): Flow<Result<DeltaUpdate>> = service.getDelta(owner, chatId, afterSequence)
        .map { result ->
            result
                .onFailure { ErrorUtils.handleError(it) }
                .map { delta ->
                    DeltaUpdate(
                        messages = delta.messages.map { it.toChatMessage() },
                        latestSequence = delta.latestSequence,
                        checkpointSequence = delta.checkpointSequence,
                    )
                }
        }

    override suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage> = service.sendMessage(owner, chatId, content, clientMessageId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toChatMessage() }

    override suspend fun editMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        content: List<MessageContent>,
        expectedEventSequence: Long,
    ): Result<ChatMessage> = service.editMessage(owner, chatId, messageId, content, expectedEventSequence)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toChatMessage() }

    override suspend fun deleteMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        expectedEventSequence: Long,
    ): Result<ChatMessage> = service.deleteMessage(owner, chatId, messageId, expectedEventSequence)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toChatMessage() }

    override suspend fun addReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> = service.addReaction(owner, chatId, messageId, emoji)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun removeReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction> = service.removeReaction(owner, chatId, messageId, emoji)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getReactors(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
        queryOptions: QueryOptions,
    ): Result<ReactorsPage> = service.getReactors(owner, chatId, messageId, emoji, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { ReactorsPage(reactors = it.reactors, hasMore = it.hasMore) }

    override suspend fun getReactionSummary(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<ReactionSummary> = service.getReactionSummary(owner, chatId, messageId)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getReactionSummaries(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<ReactionSummary>> = service.getReactionSummaries(owner, chatId, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun advancePointer(
        owner: KeyPair,
        chatId: ChatId,
        pointerType: PointerType,
        messageId: Long,
    ): Result<Unit> = service.advancePointer(owner, chatId, pointerType, messageId)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun notifyIsTyping(
        owner: KeyPair,
        chatId: ChatId,
        state: TypingState,
    ): Result<Unit> = service.notifyIsTyping(owner, chatId, state)
        .onFailure { ErrorUtils.handleError(it) }
}
