package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How much of a group's roster the device has read. No row means the chat has never been read to
 * the end.
 *
 * A table of its own rather than columns on `chat_metadata`, which is written whole from every
 * feed payload: a column there would be reset by the next feed sync.
 */
@Entity(tableName = "chat_roster_sync")
data class ChatRosterSyncEntity(
    @PrimaryKey @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    // The roster version the last full read ended at.
    @ColumnInfo(name = "synced_version") val syncedVersion: Long,
    // The last read stopped at the page cap, so the device holds fewer members than the roster
    // has. Not a reason to read again: the next read would stop at the same place.
    @ColumnInfo(name = "truncated", defaultValue = "0") val truncated: Boolean = false,
    // Set when a roster change skipped a version: what the device holds can no longer be trusted
    // to be the whole roster, and the next open reads it again.
    @ColumnInfo(name = "needs_resync", defaultValue = "0") val needsResync: Boolean = false,
)
