package com.getcode.oct24.network.repository

import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.oct24.data.mapper.ChatMessageMapper
import com.getcode.oct24.internal.network.service.MessagingService
import com.getcode.oct24.data.Room
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.user.UserManager
import com.getcode.model.chat.ChatMessage
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

class MessagingRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: MessagingService,
    private val messageMapper: ChatMessageMapper,
) {
    suspend fun getMessages(
        chat: Room,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<List<ChatMessage>> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        return service.getMessages(owner, userId, queryOptions)
            .map { it.map { meta -> messageMapper.map(chat to meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun sendMessage(chat: Room, content: OutgoingMessageContent): Result<ChatMessage> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.sendMessage(owner, chat.id, content)
            .map { messageMapper.map(chat to it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun advancePointer(chat: Room, messageId: ID, status: MessageStatus): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.advancePointer(owner, chat.id, messageId, status)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun onStartedTyping(chat: Room): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        return service.notifyIsTyping(owner, chat.id, true)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun onStoppedTyping(chat: Room): Result<Unit> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        return service.notifyIsTyping(owner, chat.id, false)
            .onFailure { ErrorUtils.handleError(it) }
    }
}