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
    suspend fun upsert(entity: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex")
    suspend fun deletePending(chatIdHex: String, clientIdHex: String)

    @Query("DELETE FROM chat_messages WHERE chat_id_hex = :chatIdHex AND status = 'SENDING'")
    suspend fun deleteAllPending(chatIdHex: String)

    @Transaction
    suspend fun confirmPendingMessage(chatIdHex: String, clientIdHex: String, serverMessage: ChatMessageEntity) {
        deletePending(chatIdHex, clientIdHex)
        upsert(serverMessage)
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
