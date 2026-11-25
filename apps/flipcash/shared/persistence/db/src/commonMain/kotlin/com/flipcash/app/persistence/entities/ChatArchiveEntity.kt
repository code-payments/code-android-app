package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An archive or unarchive the user made that the server has not confirmed yet. The server's
 * answer lives in `chat_metadata.archived_at_epoch_ms`; a row here wins over it on read until
 * the write lands, then the row is deleted.
 *
 * [archived] says which way the write goes. Rows stored before the server held archives were the
 * whole local archive, one row per archived chat, so the migration gives them `archived = 1`: they
 * are archives to upload.
 *
 * Its own table rather than a `chat_metadata` column, on purpose: `chat_metadata` is rewritten by
 * every feed sync and viewer-state payload, and a write the server has not seen yet must survive
 * both.
 *
 * Added by an `AutoMigration` rather than left to `fallbackToDestructiveMigration()`: a pending
 * write cannot be re-fetched, so a version bump that dropped the file would lose it.
 */
@Entity(tableName = "chat_archive")
data class ChatArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    // When the user archived the chat, or for an unarchive, when they unarchived it. Also tells
    // one write from the next on the same chat, so a confirmed write deletes only its own row.
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long,
    @ColumnInfo(name = "archived", defaultValue = "1")
    val archived: Boolean = true,
)
