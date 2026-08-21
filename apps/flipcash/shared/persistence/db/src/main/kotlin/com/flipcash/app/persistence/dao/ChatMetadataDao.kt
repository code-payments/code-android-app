package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMetadataDao {

    @Query("SELECT * FROM chat_metadata ORDER BY last_activity_epoch_ms DESC")
    fun observeAll(): Flow<List<ChatMetadataEntity>>

    @Query("SELECT * FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getById(chatIdHex: String): ChatMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<ChatMetadataEntity>)

    @Query("SELECT last_activity_epoch_ms FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getLastActivity(chatIdHex: String): Long?

    @Query("UPDATE chat_metadata SET last_activity_epoch_ms = :epochMs WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLastActivity(chatIdHex: String, epochMs: Long)

    @Query("UPDATE chat_metadata SET last_message_id = :messageId WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLastMessageId(chatIdHex: String, messageId: Long)

    @Query("UPDATE chat_metadata SET latest_event_sequence = :sequence WHERE chat_id_hex = :chatIdHex")
    suspend fun updateLatestEventSequence(chatIdHex: String, sequence: Long)

    @Query("SELECT latest_event_sequence FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getLatestEventSequence(chatIdHex: String): Long?

    @Query("SELECT analytics_counted_through FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getAnalyticsCountedThrough(chatIdHex: String): Long?

    // MAX() keeps the watermark monotonic even if an out-of-order write lands.
    @Query(
        "UPDATE chat_metadata SET analytics_counted_through = MAX(analytics_counted_through, :messageId) " +
            "WHERE chat_id_hex = :chatIdHex"
    )
    suspend fun advanceAnalyticsCountedThrough(chatIdHex: String, messageId: Long)

    @Query("UPDATE chat_metadata SET is_hidden = :hidden WHERE chat_id_hex = :chatIdHex")
    suspend fun updateHidden(chatIdHex: String, hidden: Boolean)

    @Query("DELETE FROM chat_metadata")
    suspend fun deleteAll()
}
