package com.getcode.oct24.internal.network.repository.messaging

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.oct24.data.Room
import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.data.mapper.ChatMessageMapper
import com.getcode.oct24.internal.data.mapper.LastMessageMapper
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.oct24.internal.network.service.ChatMessageStreamReference
import com.getcode.oct24.internal.network.service.MessagingService
import com.getcode.oct24.user.UserManager
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class RealMessagingRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: MessagingService,
    private val messageMapper: ChatMessageMapper,
    private val lastMessageMapper: LastMessageMapper,
    private val messageWithContentMapper: ConversationMessageWithContentMapper,
): MessagingRepository {
    private val db: FcAppDatabase by lazy { FcAppDatabase.requireInstance() }
    private var messageStream: ChatMessageStreamReference? = null

    override suspend fun getMessages(
        chat: Room,
        queryOptions: QueryOptions,
    ): Result<List<ChatMessage>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.getMessages(owner, userId, queryOptions)
            .map { it.map { meta -> messageMapper.map(chat to meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun sendMessage(chatId: ID, content: OutgoingMessageContent): Result<ChatMessage> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.sendMessage(owner, chatId, content)
            .map { lastMessageMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun advancePointer(chatId: ID, messageId: ID, status: MessageStatus): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.advancePointer(owner, chatId, messageId, status)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun onStartedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.notifyIsTyping(owner, chatId, true)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun onStoppedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        return service.notifyIsTyping(owner, chatId, false)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override fun openMessageStream(coroutineScope: CoroutineScope, conversation: Conversation) {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")

        messageStream = service.openMessageStream(
            scope = coroutineScope,
            owner = owner,
            chatId = conversation.id,
            lastMessageId = { db.conversationMessageDao().getNewestMessage(conversation.id)?.id }
        ) stream@{ result ->
            if (result.isSuccess) {
                val data = result.getOrNull() ?: return@stream
                val message = lastMessageMapper.map(data)

                val messageWithContents = messageWithContentMapper.map(conversation.id to message)
                coroutineScope.launch(Dispatchers.IO) {
                    db.conversationMessageDao().upsertMessagesWithContent(messageWithContents)
                }
            } else {
                result.exceptionOrNull()?.let {
                    ErrorUtils.handleError(it)
                }
            }
        }
    }

    override fun closeMessageStream() {
        messageStream?.destroy()
    }
}