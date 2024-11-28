package xyz.flipchat.services.internal.network.repository.messaging

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import xyz.flipchat.services.domain.mapper.ConversationMessageWithContentMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContent
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ChatMessageMapper
import xyz.flipchat.services.internal.data.mapper.LastMessageMapper
import xyz.flipchat.services.internal.network.service.ChatMessageStreamReference
import xyz.flipchat.services.internal.network.service.MessagingService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

internal class RealMessagingRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: MessagingService,
    private val messageMapper: ChatMessageMapper,
    private val lastMessageMapper: LastMessageMapper,
    private val messageWithContentMapper: ConversationMessageWithContentMapper,
): MessagingRepository {
    private var messageStream: ChatMessageStreamReference? = null

    override suspend fun getMessages(
        chatId: ID,
        queryOptions: QueryOptions,
    ): Result<List<ChatMessage>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.getMessages(owner, chatId, queryOptions)
            .map { it.map { meta -> messageMapper.map(userId to meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun sendMessage(chatId: ID, content: OutgoingMessageContent): Result<ChatMessage> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.sendMessage(owner, chatId, content)
            .map { lastMessageMapper.map(userId to it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun deleteMessage(chatId: ID, messageId: ID): Result<Unit> {
        return Result.failure(NotImplementedError())
    }

    override suspend fun advancePointer(chatId: ID, messageId: ID, status: MessageStatus): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.advancePointer(owner, chatId, messageId, status)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun onStartedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.notifyIsTyping(owner, chatId, true)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun onStoppedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return service.notifyIsTyping(owner, chatId, false)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override fun openMessageStream(
        coroutineScope: CoroutineScope,
        chatId: ID,
        lastMessageId: suspend () -> ID?,
        onMessageUpdate: (ConversationMessageWithContent) -> Unit
    ) {
        val owner = userManager.keyPair ?: throw IllegalStateException("No ed25519 signature found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        if (messageStream == null) {
            messageStream = service.openMessageStream(
                scope = coroutineScope,
                owner = owner,
                chatId = chatId,
                lastMessageId = lastMessageId,
            ) stream@{ result ->
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: return@stream
                    val message = lastMessageMapper.map(userId to data)

                    val messageWithContents = messageWithContentMapper.map(chatId to message)
                    onMessageUpdate(messageWithContents)
                } else {
                    result.exceptionOrNull()?.let {
                        ErrorUtils.handleError(it)
                    }
                }
            }

            messageStream?.onConnect = {
                userManager.roomOpened(roomId = chatId)
            }
        }
    }

    override fun closeMessageStream() {
        userManager.roomClosed()
        messageStream?.destroy()
        messageStream = null
    }
}