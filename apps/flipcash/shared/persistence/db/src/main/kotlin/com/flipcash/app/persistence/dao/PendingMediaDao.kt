package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.PendingMediaEntity

@Dao
interface PendingMediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PendingMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<PendingMediaEntity>)

    @Query("SELECT * FROM pending_media WHERE client_id_hex = :clientIdHex")
    suspend fun get(clientIdHex: String): PendingMediaEntity?

    /** Oldest first: entries of one send are written in chip order, and recovery posts in it. */
    @Query("SELECT * FROM pending_media ORDER BY created_at ASC, client_id_hex ASC")
    suspend fun getAll(): List<PendingMediaEntity>

    @Query("UPDATE pending_media SET stored_blob_id_hex = :blobIdHex, sealed_for_hex = :sealedForHex, size_bytes = :sizeBytes, blurhash = :blurhash WHERE client_id_hex = :clientIdHex")
    suspend fun markStored(
        clientIdHex: String,
        blobIdHex: String,
        sealedForHex: String?,
        sizeBytes: Long,
        blurhash: String?,
    )

    @Query("DELETE FROM pending_media WHERE client_id_hex = :clientIdHex")
    suspend fun delete(clientIdHex: String)

    @Query("DELETE FROM pending_media")
    suspend fun deleteAll()
}
