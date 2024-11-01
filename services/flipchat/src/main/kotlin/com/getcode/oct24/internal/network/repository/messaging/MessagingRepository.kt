package com.getcode.oct24.internal.network.repository.messaging

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageStatus
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.services.model.chat.OutgoingMessageContent
import kotlinx.coroutines.CoroutineScope

interface MessagingRepository {
    suspend fun getMessages(
        chatId: ID,
        queryOptions: QueryOptions = QueryOptions(),
    ): Result<List<ChatMessage>>
    suspend fun sendMessage(chatId: ID, content: OutgoingMessageContent): Result<ChatMessage>
    suspend fun deleteMessage(chatId: ID, messageId: ID): Result<Unit>
    suspend fun advancePointer(chatId: ID, messageId: ID, status: MessageStatus): Result<Unit>
    suspend fun onStartedTyping(chatId: ID): Result<Unit>
    suspend fun onStoppedTyping(chatId: ID): Result<Unit>
    fun openMessageStream(coroutineScope: CoroutineScope, conversation: Conversation)
    fun closeMessageStream()
}