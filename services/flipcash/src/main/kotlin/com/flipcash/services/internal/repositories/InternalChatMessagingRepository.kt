package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.extensions.toChatMessage
import com.flipcash.services.internal.network.services.MessagingService
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.repository.MessagingRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.utils.ErrorUtils

internal class InternalMessagingRepository(
    private val service: MessagingService,
) : MessagingRepository {

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

    override suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage> = service.sendMessage(owner, chatId, content, clientMessageId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toChatMessage() }

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
