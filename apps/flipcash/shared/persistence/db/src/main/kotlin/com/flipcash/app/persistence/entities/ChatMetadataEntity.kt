package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_metadata")
data class ChatMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    @ColumnInfo(name = "chat_type") val chatType: String,
    @ColumnInfo(name = "last_activity_epoch_ms", index = true) val lastActivityEpochMs: Long,
    @ColumnInfo(name = "last_message_id") val lastMessageId: Long?,
    @ColumnInfo(name = "latest_event_sequence", defaultValue = "0") val latestEventSequence: Long = 0,
)
