package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flipcash.app.persistence.entities.LinkPreviewEntity

@Dao
interface LinkPreviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preview: LinkPreviewEntity)

    /** Every stored preview. The table holds one row per distinct link, so it is read whole. */
    @Query("SELECT * FROM link_previews")
    suspend fun getAll(): List<LinkPreviewEntity>

    @Query("DELETE FROM link_previews WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Every preview last written before [cutoffMillis], the ones [deleteWrittenBefore] drops. */
    @Query("SELECT * FROM link_previews WHERE updated_at < :cutoffMillis")
    suspend fun getWrittenBefore(cutoffMillis: Long): List<LinkPreviewEntity>

    /** Drops every preview last written before [cutoffMillis]. */
    @Query("DELETE FROM link_previews WHERE updated_at < :cutoffMillis")
    suspend fun deleteWrittenBefore(cutoffMillis: Long)
}
