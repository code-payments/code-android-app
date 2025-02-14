package xyz.flipchat.services.internal.network.repository.messaging

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ChatMessageMapper
import xyz.flipchat.services.internal.data.mapper.LastMessageMapper
import xyz.flipchat.services.internal.data.mapper.TypingMapper
import xyz.flipchat.services.internal.network.chat.IsTyping
import xyz.flipchat.services.internal.network.chat.MessageStreamUpdate
import xyz.flipchat.services.internal.network.chat.TypingState
import xyz.flipchat.services.internal.network.service.ChatMessageStreamReference
import xyz.flipchat.services.internal.network.service.MessagingService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

internal class RealMessagingRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: MessagingService,
    private val chatMessageMapper: ChatMessageMapper,
    private val lastMessageMapper: LastMessageMapper,
    private val messageMapper: ConversationMessageMapper,
    private val typingMapper: TypingMapper,
): MessagingRepository {
    private var messageStream: ChatMessageStreamReference? = null

    private val typingState = MutableStateFlow<List<IsTyping>>(emptyList())

    override suspend fun getMessages(
        chatId: ID,
        queryOptions: QueryOptions,
    ): Result<List<ChatMessage>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return withContext(Dispatchers.IO) {
            service.getMessages(owner, chatId, queryOptions)
                .map { it.map { meta -> chatMessageMapper.map(userId to meta) } }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun sendMessage(chatId: ID, content: OutgoingMessageContent): Result<ChatMessage> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return withContext(Dispatchers.IO) {
            service.sendMessage(owner, chatId, content)
                .map { lastMessageMapper.map(userId to it) }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun deleteMessage(chatId: ID, messageId: ID): Result<Unit> {
        // this utilizes send message under the hood
        val content = OutgoingMessageContent.DeleteRequest(messageId)
        return sendMessage(chatId, content)
            .map { Unit }
    }

    override suspend fun advancePointer(chatId: ID, messageId: ID, status: MessageStatus): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.advancePointer(owner, chatId, messageId, status)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override fun observeTyping(chatId: ID): Flow<Boolean> {
        return typingState
            .map { it.filterNot { s -> userManager.isSelf(s.userId) } }
            .map { it.any { i -> i.currentlyTyping } }
    }

    override suspend fun onStartedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.notifyOfTypingState(owner, chatId, TypingState.Started)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun onStillTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.notifyOfTypingState(owner, chatId, TypingState.Still)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun onStoppedTyping(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.notifyOfTypingState(owner, chatId, TypingState.Stopped)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override fun openMessageStream(
        coroutineScope: CoroutineScope,
        chatId: ID,
        onMessagesUpdated: (List<ConversationMessage>) -> Unit,
    ) {
        val owner = userManager.keyPair ?: throw IllegalStateException("No ed25519 signature found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        if (messageStream == null) {
            messageStream = service.openMessageStream(
                scope = coroutineScope,
                owner = owner,
                chatId = chatId,
            ) stream@{ result ->
                if (result.isSuccess) {
                    val update = result.getOrNull() ?: return@stream
                    when (update) {
                        is MessageStreamUpdate.Messages -> {
                            val messages = update.data.map { lastMessageMapper.map(userId to it) }
                            val messagesWithContents = messages.map { messageMapper.map(chatId to it) }

                            onMessagesUpdated(messagesWithContents)
                        }
                        is MessageStreamUpdate.Pointers -> Unit
                        is MessageStreamUpdate.Typing -> {
                            typingState.value = update.data.map { typingMapper.map(it) }
                        }
                    }
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