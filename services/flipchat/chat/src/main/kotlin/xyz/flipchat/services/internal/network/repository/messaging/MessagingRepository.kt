package xyz.flipchat.services.internal.network.repository.messaging

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.services.model.chat.OutgoingMessageContent
import kotlinx.coroutines.CoroutineScope
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.query.QueryOptions

interface MessagingRepository {
    suspend fun getMessages(
        chatId: ID,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<List<ChatMessage>>
    suspend fun sendMessage(chatId: ID, content: OutgoingMessageContent): Result<ChatMessage>
    suspend fun advancePointer(chatId: ID, messageId: ID, status: MessageStatus): Result<Unit>
    suspend fun onStartedTyping(chatId: ID): Result<Unit>
    suspend fun onStoppedTyping(chatId: ID): Result<Unit>
    fun openMessageStream(coroutineScope: CoroutineScope, chatId: ID, lastMessageId: suspend () -> ID?, onMessagesUpdated: (List<ConversationMessage>) -> Unit)
    fun closeMessageStream()

    // Self Defense Room Controls
    suspend fun deleteMessage(chatId: ID, messageId: ID): Result<Unit>
}