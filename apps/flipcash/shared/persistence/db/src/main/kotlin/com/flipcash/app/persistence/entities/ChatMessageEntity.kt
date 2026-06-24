package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.flipcash.app.persistence.converters.MessageContentSerialized

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED,
}

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["chat_id_hex", "message_id"],
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
)
