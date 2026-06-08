package com.flipcash.app.persistence.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    suspend fun upsert(entity: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ChatMessageEntity>)

    @Query(
        """UPDATE chat_messages
           SET message_id = :serverMessageId, pending_client_id_hex = NULL, status = 'SENT'
           WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex"""
    )
    suspend fun confirmPendingMessage(chatIdHex: String, clientIdHex: String, serverMessageId: Long)

    @Query("UPDATE chat_messages SET status = :status WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex")
    suspend fun updatePendingStatus(chatIdHex: String, clientIdHex: String, status: MessageStatus)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
