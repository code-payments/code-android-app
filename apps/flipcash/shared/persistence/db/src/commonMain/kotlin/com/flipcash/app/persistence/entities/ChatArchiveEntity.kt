package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chat the viewer has archived. Presence of the row is the whole state.
 *
 * Its own table rather than a `chat_metadata` column, on purpose: `chat_metadata.is_hidden` is
 * server-owned and `ChatMetadataDao.updateServerOwnedFields` rewrites it on every feed sync, so a
 * client-owned flag stored there would be reset by the next sync. A row here is never touched by
 * the metadata round-trip.
 *
 * A row is only ever written by the user's own archive action and removed by their unarchive, by
 * leaving the group, or by account erasure; no incoming message, mention or payment clears it.
 *
 * Added by an `AutoMigration` rather than left to `fallbackToDestructiveMigration()`: an archive
 * set cannot be re-fetched until the server stores it, so a version bump that dropped the file
 * would lose it.
 */
@Entity(tableName = "chat_archive")
data class ChatArchiveEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long,
)
