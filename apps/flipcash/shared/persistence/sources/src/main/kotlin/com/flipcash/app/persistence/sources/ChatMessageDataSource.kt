package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PendingMessage(
    val message: ChatMessage,
    val clientMessageId: ClientMessageId,
)

@Singleton
class ChatMessageDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> =
        db?.chatMessageDao()?.observeMessages(mapper.chatIdHex(chatId))?.map { entities ->
            entities.map { mapper.toMessage(it) }
        } ?: emptyFlow()

    suspend fun getLatest(chatIdHex: String): ChatMessage? =
        db?.chatMessageDao()?.getLatest(chatIdHex)?.let { mapper.toMessage(it) }

    suspend fun upsert(chatId: ChatId, messages: List<ChatMessage>) {
        val hex = mapper.chatIdHex(chatId)
        db?.chatMessageDao()?.upsert(messages.map { mapper.toEntity(hex, it) })
    }

    suspend fun insertPending(
        chatId: ChatId,
        content: List<MessageContent>,
        senderId: ID,
    ): PendingMessage {
        val clientMessageId = ClientMessageId(RandomId.toByteArray())
        val entity = mapper.toPendingEntity(
            chatIdHex = mapper.chatIdHex(chatId),
            content = content,
            senderId = senderId,
            clientMessageId = clientMessageId,
        )
        db?.chatMessageDao()?.upsert(entity)
        return PendingMessage(
            message = mapper.toMessage(entity),
            clientMessageId = clientMessageId,
        )
    }

    suspend fun confirmPending(chatId: ChatId, clientMessageId: ClientMessageId, serverMessageId: Long) {
        db?.chatMessageDao()?.confirmPendingMessage(
            mapper.chatIdHex(chatId),
            mapper.clientMessageIdHex(clientMessageId),
            serverMessageId,
        )
    }

    suspend fun failPending(chatId: ChatId, clientMessageId: ClientMessageId) {
        db?.chatMessageDao()?.updatePendingStatus(
            mapper.chatIdHex(chatId),
            mapper.clientMessageIdHex(clientMessageId),
            MessageStatus.FAILED,
        )
    }

    suspend fun clear() {
        db?.chatMessageDao()?.deleteAll()
    }
}
