package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chat the viewer marked as unread. Presence of the row is the whole state.
 *
 * Its own table for the same reason as [ChatArchiveEntity]: `chat_metadata` is rewritten by every
 * feed sync, and the mark is client-owned until the server stores it.
 *
 * A row is written by Mark as Unread and removed by Mark as Read, Read All, opening the chat,
 * leaving the group, or account erasure. A new message in the chat leaves it alone.
 */
@Entity(tableName = "chat_marked_unread")
data class ChatMarkedUnreadEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    @ColumnInfo(name = "marked_at")
    val markedAt: Long,
)
