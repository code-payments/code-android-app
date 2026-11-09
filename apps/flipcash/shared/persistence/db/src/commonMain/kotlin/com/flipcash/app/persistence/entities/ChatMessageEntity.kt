package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.flipcash.app.persistence.ChatPartialIndexes
import com.flipcash.app.persistence.converters.MessageContentSerialized

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED,
    /**
     * The server refused the send (`Denied`). Final: a retry gets the same answer. Stored by name,
     * so no migration; a build that predates it reads the row as SENT.
     */
    DENIED,
}

/** How an end-to-end encrypted row stands on this device. Null on a row sent in plaintext. */
enum class EncryptionState {
    /** [ChatMessageEntity.contentJson] is the plaintext. */
    DECRYPTED,

    /**
     * A key fetch failed, so the row hasn't been opened yet. Hidden from the transcript and the
     * chat list until it is.
     */
    KEY_PENDING,

    /** An unknown scheme, or a plaintext type this client doesn't render. */
    UNSUPPORTED,

    /** The ciphertext failed authentication. */
    AUTH_FAILED,
}

/**
 * The transcript reads this table one page at a time, ordered newest-first within a chat, and the
 * composite primary key `(chat_id_hex, message_id)` does not serve that order. Without the index
 * every page is a fresh scan and sort of the whole table: walking 2,000 rows back in a 40,000-row
 * table measured 536 ms unindexed against 20 ms indexed.
 *
 * The other three serve the scans that run at every login, before the chat list paints (see
 * [ChatPartialIndexes]). On disk each is partial, holding only the few rows its scan looks for, but
 * `@Index` can't say `WHERE`: these declarations name the columns so Room's schema check, which
 * compares name, columns and uniqueness and never the `WHERE`, accepts them. The exported schema
 * therefore shows full indexes. Anything Room creates from these, a fresh install or a later
 * migration that rebuilds this table, is made partial again when the database opens.
 */
@Entity(
    tableName = "chat_messages",
    primaryKeys = ["chat_id_hex", "message_id"],
    indices = [
        Index(
            value = ["chat_id_hex", "timestamp_epoch_ms"],
            name = "index_chat_messages_chat_id_hex_timestamp_epoch_ms",
        ),
        Index(value = ["status"], name = ChatPartialIndexes.SENDING),
        Index(value = ["encryption_state"], name = ChatPartialIndexes.KEY_PENDING),
        Index(value = ["chat_id_hex", "pending_client_id_hex"], name = ChatPartialIndexes.CLIENT_ID),
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
    /**
     * The message as it arrived, when it arrived end-to-end encrypted: one serialized
     * `Encrypted`. Kept beside the plaintext in [contentJson] so a later copy of the same message
     * is recognised without opening it again.
     */
    @ColumnInfo(name = "ciphertext_json") val ciphertextJson: List<MessageContentSerialized>? = null,
    @ColumnInfo(name = "encryption_state") val encryptionState: EncryptionState? = null,
)
