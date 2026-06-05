package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.repository.MessagingRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingController @Inject constructor(
    private val repository: MessagingRepository,
    private val userManager: UserManager,
) {
    private fun requireOwner() = userManager.accountCluster?.authority?.keyPair
        ?: throw IllegalStateException("No account cluster in UserManager")

    suspend fun getMessage(chatId: ChatId, messageId: Long): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessage(owner, chatId, messageId)
    }

    suspend fun getMessages(chatId: ChatId, queryOptions: QueryOptions = QueryOptions()): Result<List<ChatMessage>> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessages(owner, chatId, queryOptions)
    }

    suspend fun getMessagesByIds(chatId: ChatId, messageIds: List<Long>): Result<List<ChatMessage>> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.getMessagesByIds(owner, chatId, messageIds)
    }

    suspend fun sendMessage(
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage> {
        val owner = runCatching { requireOwner() }.getOrElse { return Result.failure(it) }
        return repository.sendMessage(owner, chatId, content, clientMessageId)
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
