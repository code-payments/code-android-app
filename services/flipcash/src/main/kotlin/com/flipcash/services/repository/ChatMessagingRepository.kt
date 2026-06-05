package com.flipcash.services.repository

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.getcode.ed25519.Ed25519.KeyPair

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

    suspend fun sendMessage(
        owner: KeyPair,
        chatId: ChatId,
        content: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage>

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
