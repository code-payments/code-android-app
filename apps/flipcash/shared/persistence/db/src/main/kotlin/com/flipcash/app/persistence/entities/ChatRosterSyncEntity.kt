package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How far the device's copy of a group's roster can be trusted. No row means nothing is known:
 * the roster has never been read to the end, and no watermark has been set.
 *
 * A table of its own rather than columns on `chat_metadata`, which is written whole from every
 * feed payload: a column there would be reset by the next feed sync. For the same reason the
 * watermark is not `chat_metadata.roster_version`, which a feed payload can move past changes the
 * device never applied.
 */
@Entity(tableName = "chat_roster_sync")
data class ChatRosterSyncEntity(
    @PrimaryKey @ColumnInfo(name = "chat_id_hex") val chatIdHex: String,
    // The last roster version whose joins the device holds. A roster summary above it means joins
    // were missed, and a read from the top of the roster down to it recovers them.
    @ColumnInfo(name = "watermark") val watermark: Long,
    // A read of the whole roster has finished at least once.
    @ColumnInfo(name = "fully_synced", defaultValue = "0") val fullySynced: Boolean = false,
    // The last full read stopped at the page cap, so the device holds fewer members than the
    // roster has. Not a reason to read again: the next read would stop at the same place.
    @ColumnInfo(name = "truncated", defaultValue = "0") val truncated: Boolean = false,
    // Someone left while the device was not listening, and only a full read can say who. Set until
    // that read finishes; search keeps the departed member until then.
    @ColumnInfo(name = "reconcile_pending", defaultValue = "0") val reconcilePending: Boolean = false,
)
