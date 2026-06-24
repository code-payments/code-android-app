package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms ASC")
    fun observeMessages(chatIdHex: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms DESC")
    fun observeMessagesPaged(chatIdHex: String): PagingSource<Int, ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms DESC LIMIT 1")
    suspend fun getLatest(chatIdHex: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex LIMIT 1")
    suspend fun getByClientId(chatIdHex: String, clientIdHex: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entities: List<ChatMessageEntity>)

    @Query("SELECT pending_client_id_hex FROM chat_messages WHERE chat_id_hex = :chatIdHex AND message_id = :messageId")
    suspend fun getPendingClientId(chatIdHex: String, messageId: Long): String?

    @Query("SELECT event_sequence FROM chat_messages WHERE chat_id_hex = :chatIdHex AND message_id = :messageId")
    suspend fun getEventSequence(chatIdHex: String, messageId: Long): Long?

    @Transaction
    suspend fun upsert(entity: ChatMessageEntity) {
        // Event-sequence guard: skip if stored sequence is newer (last-writer-wins).
        // Passthrough when eventSequence == 0 (legacy messages).
        if (entity.eventSequence > 0) {
            val stored = getEventSequence(entity.chatIdHex, entity.messageId)
            if (stored != null && stored > entity.eventSequence) return
        }

        val existingPendingId = getPendingClientId(entity.chatIdHex, entity.messageId)
        val merged = if (existingPendingId != null && entity.pendingClientIdHex == null) {
            entity.copy(pendingClientIdHex = existingPendingId)
        } else entity
        insert(merged)
    }

    @Transaction
    suspend fun upsert(entities: List<ChatMessageEntity>) {
        for (entity in entities) upsert(entity)
    }

    @Query("DELETE FROM chat_messages WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex")
    suspend fun deletePending(chatIdHex: String, clientIdHex: String)

    @Query("SELECT pending_client_id_hex FROM chat_messages WHERE chat_id_hex = :chatIdHex AND status = 'SENDING' AND pending_client_id_hex IS NOT NULL")
    suspend fun getPendingClientIds(chatIdHex: String): List<String>

    @Query("DELETE FROM chat_messages WHERE chat_id_hex = :chatIdHex AND status = 'SENDING'")
    suspend fun deleteAllPending(chatIdHex: String)

    @Query("""
        UPDATE chat_messages
        SET message_id = :newMessageId,
            timestamp_epoch_ms = :newTimestampMs,
            unread_seq = :newUnreadSeq,
            status = 'SENT'
        WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex
    """)
    suspend fun updatePendingToConfirmed(
        chatIdHex: String,
        clientIdHex: String,
        newMessageId: Long,
        newTimestampMs: Long,
        newUnreadSeq: Long,
    )

    @Transaction
    suspend fun confirmPendingMessage(chatIdHex: String, clientIdHex: String, serverMessage: ChatMessageEntity) {
        updatePendingToConfirmed(
            chatIdHex = chatIdHex,
            clientIdHex = clientIdHex,
            newMessageId = serverMessage.messageId,
            newTimestampMs = serverMessage.timestampEpochMs,
            newUnreadSeq = serverMessage.unreadSeq,
        )
    }

    @Transaction
    suspend fun upsertAndClearPending(chatIdHex: String, entities: List<ChatMessageEntity>) {
        deleteAllPending(chatIdHex)
        upsert(entities)
    }

    @Query("UPDATE chat_messages SET status = :status WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex")
    suspend fun updatePendingStatus(chatIdHex: String, clientIdHex: String, status: MessageStatus)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
