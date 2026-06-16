package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.persistence.PagingDataSource
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
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
    private val userManager: UserManager,
) : PagingDataSource<Long, ChatMessage, List<ChatMessage>, Int, ChatMessageEntity> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    private var activeChatId: ChatId? = null

    fun setActiveChatId(chatId: ChatId) {
        activeChatId = chatId
    }

    // region PagingDataSource

    override fun observe(): PagingSource<Int, ChatMessageEntity> {
        val id = activeChatId ?: return emptyPagingSource()
        return db?.chatMessageDao()?.observeMessagesPaged(mapper.chatIdHex(id))
            ?: emptyPagingSource()
    }

    override suspend fun getById(id: Long): ChatMessage? {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return null
        return db?.chatMessageDao()?.getLatest(hex)?.let { toChatMessage(it) }
    }

    override suspend fun get(): List<ChatMessage> {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return emptyList()
        return db?.chatMessageDao()?.observeMessages(hex)
            ?.firstOrNull()
            ?.map { toChatMessage(it) }
            ?: emptyList()
    }

    override suspend fun getMostRecent(): ChatMessage? {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return null
        return getLatest(hex)
    }

    override suspend fun query(whereClause: String): List<ChatMessage> = emptyList()

    override suspend fun upsert(value: List<ChatMessage>) {
        val chatId = activeChatId ?: return
        upsert(chatId, value)
    }

    override suspend fun clear() {
        db?.chatMessageDao()?.deleteAll()
    }

    // endregion

    fun observeForChat(chatId: ChatId): PagingSource<Int, ChatMessageEntity> {
        return db?.chatMessageDao()?.observeMessagesPaged(mapper.chatIdHex(chatId))
            ?: emptyPagingSource()
    }

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> =
        db?.chatMessageDao()?.observeMessages(mapper.chatIdHex(chatId))?.map { entities ->
            entities.map { toChatMessage(it) }
        } ?: emptyFlow()

    suspend fun getLatest(chatIdHex: String): ChatMessage? =
        db?.chatMessageDao()?.getLatest(chatIdHex)?.let { toChatMessage(it) }

    suspend fun hasMessages(chatId: ChatId): Boolean =
        db?.chatMessageDao()?.getLatest(mapper.chatIdHex(chatId)) != null

    suspend fun getLatestMessageId(chatId: ChatId): Long? =
        db?.chatMessageDao()?.getLatest(mapper.chatIdHex(chatId))?.messageId

    suspend fun upsert(chatId: ChatId, messages: List<ChatMessage>) {
        val hex = mapper.chatIdHex(chatId)
        val entities = messages.map { mapper.toEntity(hex, it) }
        val selfId = userManager.accountId
        val hasSelfMessage = selfId != null && messages.any { it.senderId == selfId }
        if (hasSelfMessage) {
            db?.chatMessageDao()?.upsertAndClearPending(hex, entities)
        } else {
            db?.chatMessageDao()?.upsert(entities)
        }
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
            message = mapper.toMessage(entity).copy(isFromSelf = true),
            clientMessageId = clientMessageId,
        )
    }

    suspend fun confirmPending(chatId: ChatId, clientMessageId: ClientMessageId, serverMessage: ChatMessage) {
        val hex = mapper.chatIdHex(chatId)
        db?.chatMessageDao()?.confirmPendingMessage(
            hex,
            mapper.clientMessageIdHex(clientMessageId),
            mapper.toEntity(hex, serverMessage),
        )
    }

    suspend fun failPending(chatId: ChatId, clientMessageId: ClientMessageId) {
        db?.chatMessageDao()?.updatePendingStatus(
            mapper.chatIdHex(chatId),
            mapper.clientMessageIdHex(clientMessageId),
            MessageStatus.FAILED,
        )
    }

    fun toChatMessage(entity: ChatMessageEntity): ChatMessage {
        val message = mapper.toMessage(entity)
        val selfId = userManager.accountId
        return message.copy(isFromSelf = selfId != null && message.senderId == selfId)
    }

    private fun emptyPagingSource() = object : PagingSource<Int, ChatMessageEntity>() {
        override fun getRefreshKey(state: androidx.paging.PagingState<Int, ChatMessageEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatMessageEntity> =
            LoadResult.Error(Exception("Database not initialized"))
    }
}
