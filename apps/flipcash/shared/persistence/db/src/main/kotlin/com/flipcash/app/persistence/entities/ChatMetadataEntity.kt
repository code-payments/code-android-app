package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.flipcash.app.persistence.converters.ChatRulesSerialized
import com.flipcash.services.models.chat.MediaItem

@Entity(tableName = "chat_metadata")
data class ChatMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    @ColumnInfo(name = "chat_type")
    val chatType: String,
    @ColumnInfo(name = "last_activity_epoch_ms", index = true)
    val lastActivityEpochMs: Long,
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: Long?,
    @ColumnInfo(name = "latest_event_sequence", defaultValue = "0")
    val latestEventSequence: Long = 0,
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,
    // Highest message id already counted into the received-analytics people
    // properties. Monotonic; guards non-idempotent increments against replay.
    @ColumnInfo(name = "analytics_counted_through", defaultValue = "0")
    val analyticsCountedThrough: Long = 0,
    // Group identity. Null on a DM, where the counterparty's profile is the title and avatar.
    @ColumnInfo(name = "title")
    val title: String? = null,
    @ColumnInfo(name = "picture_json")
    val pictureJson: MediaItem? = null,
    // The roster's true size and the version that decides whether a roster write applies.
    // Zero on a DM, and on any group whose metadata was rebuilt without a server round trip.
    @ColumnInfo(name = "member_count", defaultValue = "0")
    val memberCount: Long = 0,
    @ColumnInfo(name = "roster_version", defaultValue = "0")
    val rosterVersion: Long = 0,
    @ColumnInfo(name = "rules_json")
    val rulesJson: ChatRulesSerialized? = null,
    // Carried explicitly rather than inferred from the row's presence. Every group that reaches
    // the device today is one you are in, so this is constant for now — but membership is what
    // the gate, removal reconciliation and LeaveChat all turn on, and a state that important
    // should be readable in the table rather than implied by whether a row exists.
    @ColumnInfo(name = "is_member", defaultValue = "1")
    val isMember: Boolean = true,
)
