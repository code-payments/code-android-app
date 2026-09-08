package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.flipcash.app.persistence.converters.MessageContentSerialized

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED,
}

/**
 * The transcript reads this table one page at a time, ordered newest-first within a chat, and the
 * composite primary key `(chat_id_hex, message_id)` does not serve that order. Without the index
 * every page is a fresh scan and sort of the whole table: walking 2,000 rows back in a 40,000-row
 * table measured 536 ms unindexed against 20 ms indexed.
 */
@Entity(
    tableName = "chat_messages",
    primaryKeys = ["chat_id_hex", "message_id"],
    indices = [
        Index(
            value = ["chat_id_hex", "timestamp_epoch_ms"],
            name = "index_chat_messages_chat_id_hex_timestamp_epoch_ms",
        ),
    ],
)
data class ChatMessageEntity(
    @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "sender_id_hex") val senderIdHex: String?,
    @ColumnInfo(name = "content_json") val contentJson: List<MessageContentSerialized>?,
    @ColumnInfo(name = "timestamp_epoch_ms") val timestampEpochMs: Long,
    @ColumnInfo(name = "unread_seq") val unreadSeq: Long,
    @ColumnInfo(name = "status", defaultValue = "SENT") val status: MessageStatus = MessageStatus.SENT,
    @ColumnInfo(name = "pending_client_id_hex") val pendingClientIdHex: String? = null,
    @ColumnInfo(name = "event_sequence", defaultValue = "0") val eventSequence: Long = 0,
    @ColumnInfo(name = "last_edited_ts_epoch_ms") val lastEditedTsEpochMs: Long? = null,
    @ColumnInfo(name = "reactions_json") val reactionsJson: String? = null,
    /**
     * The row is a tombstone — the message was deleted and its content is gone.
     *
     * Kept as a column rather than derived from [contentJson] so the feed's "newest *visible*
     * message" query can filter in SQL. Matching on the serialized discriminator would work until
     * someone sends a message whose text happens to contain it.
     */
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
)
