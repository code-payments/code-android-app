package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.PendingMediaEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One queued photo message, as `pending_media` holds it. Ids stay hex strings on this side of the
 * boundary: the chat layer that writes them owns what they mean.
 */
data class PendingMediaRecord(
    val clientIdHex: String,
    val chatIdHex: String,
    val fileName: String,
    val caption: String?,
    val replyToMessageId: Long?,
    val storedBlobIdHex: String?,
    val sealedForHex: String?,
    val width: Int,
    val height: Int,
    val blurhash: String?,
    val sizeBytes: Long?,
    val createdAt: Long,
) {
    val isStored: Boolean get() = storedBlobIdHex != null
}

/**
 * The `pending_media` table. With no database there is nothing queued, which matches the other data
 * sources: the instance is null only before login and after logout.
 */
@Singleton
class PendingMediaDataSource @Inject constructor() {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    suspend fun insert(records: List<PendingMediaRecord>) {
        db?.pendingMediaDao()?.upsert(records.map { it.toEntity() })
    }

    suspend fun get(clientIdHex: String): PendingMediaRecord? =
        db?.pendingMediaDao()?.get(clientIdHex)?.toRecord()

    suspend fun getAll(): List<PendingMediaRecord> =
        db?.pendingMediaDao()?.getAll().orEmpty().map { it.toRecord() }

    /** Records that the bytes are in storage under [blobIdHex], so a retry polls instead of uploading. */
    suspend fun markStored(
        clientIdHex: String,
        blobIdHex: String,
        sealedForHex: String?,
        sizeBytes: Long,
        blurhash: String?,
    ) {
        db?.pendingMediaDao()?.markStored(clientIdHex, blobIdHex, sealedForHex, sizeBytes, blurhash)
    }

    suspend fun delete(clientIdHex: String) {
        db?.pendingMediaDao()?.delete(clientIdHex)
    }

    suspend fun clear() {
        db?.pendingMediaDao()?.deleteAll()
    }

    private fun PendingMediaRecord.toEntity() = PendingMediaEntity(
        clientIdHex = clientIdHex,
        chatIdHex = chatIdHex,
        fileName = fileName,
        caption = caption,
        replyToMessageId = replyToMessageId,
        storedBlobIdHex = storedBlobIdHex,
        sealedForHex = sealedForHex,
        width = width,
        height = height,
        blurhash = blurhash,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
    )

    private fun PendingMediaEntity.toRecord() = PendingMediaRecord(
        clientIdHex = clientIdHex,
        chatIdHex = chatIdHex,
        fileName = fileName,
        caption = caption,
        replyToMessageId = replyToMessageId,
        storedBlobIdHex = storedBlobIdHex,
        sealedForHex = sealedForHex,
        width = width,
        height = height,
        blurhash = blurhash,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
    )
}
