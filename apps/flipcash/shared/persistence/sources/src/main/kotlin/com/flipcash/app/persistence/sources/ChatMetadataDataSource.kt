package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMetadataDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
) {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    fun observeAll(): Flow<List<ChatMetadataEntity>> =
        db?.chatMetadataDao()?.observeAll() ?: emptyFlow()

    suspend fun upsert(metadata: ChatMetadata) {
        db?.chatMetadataDao()?.upsert(mapper.toEntity(metadata))
    }

    suspend fun upsert(metadatas: List<ChatMetadata>) {
        db?.chatMetadataDao()?.upsert(metadatas.map { mapper.toEntity(it) })
    }

    suspend fun updateLastActivity(chatId: ChatId, epochMs: Long) {
        db?.chatMetadataDao()?.updateLastActivity(mapper.chatIdHex(chatId), epochMs)
    }

    suspend fun updateLastMessageId(chatId: ChatId, messageId: Long) {
        db?.chatMetadataDao()?.updateLastMessageId(mapper.chatIdHex(chatId), messageId)
    }

    fun toMetadata(
        entity: ChatMetadataEntity,
        members: List<ChatMember>,
        lastMessage: ChatMessage?,
    ): ChatMetadata = mapper.toMetadata(entity, members, lastMessage)

    suspend fun clear() {
        db?.chatMetadataDao()?.deleteAll()
    }
}
