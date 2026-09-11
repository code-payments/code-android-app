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

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms ASC, message_id ASC")
    fun observeMessages(chatIdHex: String): Flow<List<ChatMessageEntity>>

    /**
     * `message_id` breaks ties because `timestamp_epoch_ms` alone is not a total order: messages
     * sent in a burst, or stamped from one server clock read, share a millisecond. A paged read
     * re-queries per page, so an unstable order there duplicates or skips a row across the seam.
     */
    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms DESC, message_id DESC")
    fun observeMessagesPaged(chatIdHex: String): PagingSource<Int, ChatMessageEntity>

    /**
     * True once the user has sent a tip — an **outgoing** Cash message (sender = self) with verb
     * TIPPED. Received tips don't count toward the "scanned a tip card" onboarding milestone.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM chat_messages WHERE sender_id_hex = :selfIdHex AND content_json LIKE '%\"action\":\"TIPPED\"%')")
    fun hasEverTipped(selfIdHex: String): Flow<Boolean>

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex ORDER BY timestamp_epoch_ms DESC LIMIT 1")
    suspend fun getLatest(chatIdHex: String): ChatMessageEntity?

    /**
     * The newest message that still has content — tombstones skipped.
     *
     * This is what the conversation list previews and what its unread check reads, so deleting the
     * newest message falls the row back to the one before it instead of reading "Message deleted".
     * Deliberately separate from [getLatest]: identity-keyed anchors (mark-read, receive buzz) need
     * the newest id including tombstones, or a delete would regress the read pointer and leave the
     * chat unread forever.
     */
    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex AND is_deleted = 0 ORDER BY timestamp_epoch_ms DESC LIMIT 1")
    suspend fun getLatestVisible(chatIdHex: String): ChatMessageEntity?

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE chat_id_hex = :chatIdHex " +
            "AND sender_id_hex IS NOT NULL AND sender_id_hex != :selfIdHex " +
            "AND message_id > :afterId AND message_id <= :throughId " +
            "ORDER BY message_id ASC"
    )
    suspend fun getInboundMessagesInRange(
        chatIdHex: String,
        selfIdHex: String,
        afterId: Long,
        throughId: Long,
    ): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex LIMIT 1")
    suspend fun getByClientId(chatIdHex: String, clientIdHex: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE chat_id_hex = :chatIdHex AND message_id = :messageId LIMIT 1")
    suspend fun getMessage(chatIdHex: String, messageId: Long): ChatMessageEntity?

    /**
     * How many messages in [chatIdHex] are newer than [timestampEpochMs] — the distance back from
     * the newest message, which is what bounds the walk to a quoted message.
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE chat_id_hex = :chatIdHex AND timestamp_epoch_ms > :timestampEpochMs")
    suspend fun countNewerThan(chatIdHex: String, timestampEpochMs: Long): Int

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
        // Event-sequence guard: skip if the stored sequence is strictly newer (last-writer-wins).
        // messaging.v1 tells clients to ignore a copy at or below the version they hold; the
        // comparison here is strict instead, because a confirmed send needs the equal case.
        // confirmPendingMessage stamps the server's event_sequence onto the optimistic row without
        // replacing the content written locally, so the server's canonical copy of that message
        // arrives at a sequence equal to the one already stored. Dropping it would pin the
        // optimistic content forever.
        //
        // Passthrough when eventSequence == 0: a legacy row or an optimistic one the server has
        // not echoed yet. The server cannot send 0 — messaging.v1 constrains event_sequence to >= 1.
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

    /**
     * The optimistic row is written before the server has stamped the message, so every
     * server-assigned field is written here — `event_sequence` included. Leaving it at the pending
     * row's 0 would keep a sent message looking unacknowledged until some later fetch of the chat
     * overwrote the row, which is what the edit/delete guards and last-writer-wins read it for.
     */
    @Query("""
        UPDATE chat_messages
        SET message_id = :newMessageId,
            timestamp_epoch_ms = :newTimestampMs,
            unread_seq = :newUnreadSeq,
            event_sequence = :newEventSequence,
            status = 'SENT'
        WHERE chat_id_hex = :chatIdHex AND pending_client_id_hex = :clientIdHex
    """)
    suspend fun updatePendingToConfirmed(
        chatIdHex: String,
        clientIdHex: String,
        newMessageId: Long,
        newTimestampMs: Long,
        newUnreadSeq: Long,
        newEventSequence: Long,
    )

    @Transaction
    suspend fun confirmPendingMessage(chatIdHex: String, clientIdHex: String, serverMessage: ChatMessageEntity) {
        updatePendingToConfirmed(
            chatIdHex = chatIdHex,
            clientIdHex = clientIdHex,
            newMessageId = serverMessage.messageId,
            newTimestampMs = serverMessage.timestampEpochMs,
            newUnreadSeq = serverMessage.unreadSeq,
            newEventSequence = serverMessage.eventSequence,
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
