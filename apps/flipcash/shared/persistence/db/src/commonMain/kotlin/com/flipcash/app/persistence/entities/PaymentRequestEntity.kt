package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment request as the viewer last received it, one row per request.
 *
 * [request] is the serialized `paymentrequest.v1.PaymentRequestMetadata`, text still in its
 * encrypted form where the chat is private (the same rule `chat_messages` follows: the database
 * is not encrypted, so only what the server sent in the clear is stored in the clear). The other
 * columns are read for queries and for the merge in `PaymentRequestDao.mergeIfNewer`; the proto
 * is the source of truth for everything else, so a new field never needs a migration.
 *
 * Added by an `AutoMigration` rather than left to `fallbackToDestructiveMigration()`, like the
 * other tables here, so a version bump never drops the file.
 */
@Entity(
    tableName = "payment_requests",
    indices = [Index(value = ["chat_id_hex"])],
)
data class PaymentRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_hex")
    val idHex: String,
    // Empty for a request of a kind the app doesn't know, which has no chat to list it under.
    @ColumnInfo(name = "chat_id_hex")
    val chatIdHex: String,
    // Compared by the merge: the greater version wins.
    @ColumnInfo(name = "version")
    val version: Long,
    // STATUS_* below, compared by the merge at an equal version.
    @ColumnInfo(name = "status")
    val status: String,
    // Epoch millis of the split's deadline; null for a request with no split.
    @ColumnInfo(name = "ends_at")
    val endsAtEpochMs: Long?,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "request", typeAffinity = ColumnInfo.BLOB)
    val request: ByteArray,
) {
    // The array is compared by content, unlike a data class's default.
    override fun equals(other: Any?): Boolean =
        this === other || (other is PaymentRequestEntity &&
            idHex == other.idHex && chatIdHex == other.chatIdHex && version == other.version &&
            status == other.status && endsAtEpochMs == other.endsAtEpochMs &&
            createdAtEpochMs == other.createdAtEpochMs && request.contentEquals(other.request))

    override fun hashCode(): Int = 31 * idHex.hashCode() + version.hashCode()

    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CLOSED = "CLOSED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_UNKNOWN = "UNKNOWN"
    }
}
