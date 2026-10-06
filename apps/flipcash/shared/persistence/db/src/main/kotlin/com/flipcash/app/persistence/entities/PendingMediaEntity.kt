package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A photo message the viewer has queued, and what it takes to finish sending it after the process
 * dies. The optimistic `chat_messages` row says that a message is on its way; this says how to get
 * it there.
 *
 * The encoded JPEG lives in `filesDir/pending-media/[fileName]`, written once, so a retry resends
 * those bytes instead of encoding the photo again. [storedBlobIdHex] is set once the bytes are in
 * storage: an entry with one resumes by polling for finalization and posting, an entry without one
 * has to upload again.
 *
 * Keyed by the message's client id, the same one `chat_messages.pending_client_id_hex` carries, so
 * a retry posts under the id the server dedupes on. Deleted once the message is confirmed or can
 * no longer be sent (the server refused the photo).
 *
 * An AutoMigration creates it rather than the destructive fallback: an entry that is dropped takes
 * a photo the viewer has not sent yet with it.
 */
@Entity(tableName = "pending_media")
data class PendingMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "client_id_hex")
    val clientIdHex: String,

    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "caption")
    val caption: String?,

    @ColumnInfo(name = "reply_to_message_id")
    val replyToMessageId: Long?,

    @ColumnInfo(name = "stored_blob_id_hex")
    val storedBlobIdHex: String?,

    /** The chat the stored blob was sealed for, or null if it was uploaded plain. */
    @ColumnInfo(name = "sealed_for_hex")
    val sealedForHex: String?,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "blurhash")
    val blurhash: String?,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
