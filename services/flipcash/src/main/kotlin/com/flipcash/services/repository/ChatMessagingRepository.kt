package com.flipcash.services.repository

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair
import kotlinx.coroutines.flow.Flow

interface ChatMessagingRepository {
    suspend fun getMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<ChatMessage>

    suspend fun getMessages(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<ChatMessage>>

    suspend fun getMessagesByIds(
        owner: KeyPair,
        chatId: ChatId,
        messageIds: List<Long>,
    ): Result<List<ChatMessage>>

    fun getDelta(
        owner: KeyPair,
        chatId: ChatId,
        afterSequence: Long,
    ): Flow<Result<DeltaUpdate>>

    suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage>

    suspend fun editMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        content: List<MessageContent>,
        expectedEventSequence: Long,
    ): Result<ChatMessage>

    suspend fun deleteMessage(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        expectedEventSequence: Long,
    ): Result<ChatMessage>

    suspend fun addReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction>

    suspend fun removeReaction(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
    ): Result<EmojiReaction>

    suspend fun getReactors(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
        emoji: Emoji,
        queryOptions: QueryOptions,
    ): Result<ReactorsPage>

    suspend fun getReactionSummary(
        owner: KeyPair,
        chatId: ChatId,
        messageId: Long,
    ): Result<ReactionSummary>

    suspend fun getReactionSummaries(
        owner: KeyPair,
        chatId: ChatId,
        queryOptions: QueryOptions,
    ): Result<List<ReactionSummary>>

    suspend fun advancePointer(
        owner: KeyPair,
        chatId: ChatId,
        pointerType: PointerType,
        messageId: Long,
    ): Result<Unit>

    suspend fun notifyIsTyping(
        owner: KeyPair,
        chatId: ChatId,
        state: TypingState,
    ): Result<Unit>
}

data class DeltaUpdate(
    val messages: List<ChatMessage>,
    val latestSequence: Long,
    val checkpointSequence: Long,
)

data class ReactorsPage(
    val reactors: List<Reactor>,
    val hasMore: Boolean,
)
