package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMetadataDao {

    @Query("SELECT * FROM chat_metadata ORDER BY last_activity_epoch_ms DESC")
    fun observeAll(): Flow<List<ChatMetadataEntity>>

    @Query("SELECT * FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getById(chatIdHex: String): ChatMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ChatMetadataEntity): Long

    /**
     * Overwrites only the columns the server owns. `latest_event_sequence` and
     * `analytics_counted_through` are client-owned watermarks that no server payload
     * carries, so they are deliberately absent here.
     */
    @Query(
        "UPDATE chat_metadata SET chat_type = :chatType, " +
            "last_activity_epoch_ms = :lastActivityEpochMs, " +
            "last_message_id = :lastMessageId, " +
            "is_hidden = :isHidden " +
            "WHERE chat_id_hex = :chatIdHex"
    )
    suspend fun updateServerOwnedFields(
        chatIdHex: String,
        chatType: String,
        lastActivityEpochMs: Long,
        lastMessageId: Long?,
        isHidden: Boolean,
    )

    /**
     * Inserts a new chat, or refreshes an existing one's server-owned columns in place.
     *
     * Deliberately not a whole-row REPLACE: `latest_event_sequence` is the cursor the
     * client has actually applied from the event log, and `analytics_counted_through`
     * is the replay guard for received-message analytics. Neither is carried by a feed
     * payload, so replacing the row would reset both — the cursor to whatever the server
     * reported as its head (skipping every unapplied event on the next `GetDelta`) and
     * the analytics watermark to zero (re-counting messages already counted).
     */
    @Transaction
    suspend fun upsert(entity: ChatMetadataEntity) {
        if (insertIfAbsent(entity) != -1L) return
        updateServerOwnedFields(
            chatIdHex = entity.chatIdHex,
            chatType = entity.chatType,
            lastActivityEpochMs = entity.lastActivityEpochMs,
            lastMessageId = entity.lastMessageId,
            isHidden = entity.isHidden,
        )
    }

    @Transaction
    suspend fun upsert(entities: List<ChatMetadataEntity>) {
        for (entity in entities) upsert(entity)
    }

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

    @Query("SELECT chat_type FROM chat_metadata WHERE chat_id_hex = :chatIdHex")
    suspend fun getChatType(chatIdHex: String): String?

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
