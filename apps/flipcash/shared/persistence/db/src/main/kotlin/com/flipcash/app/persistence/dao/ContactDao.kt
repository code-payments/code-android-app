package com.flipcash.app.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flipcash.app.persistence.entities.ContactMappingEntity
import com.flipcash.app.persistence.entities.ContactSyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    // region Sync state

    @Query("SELECT * FROM contact_sync_state WHERE id = 0")
    suspend fun getSyncState(): ContactSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: ContactSyncStateEntity)

    @Query("DELETE FROM contact_sync_state")
    suspend fun clearSyncState()

    // endregion

    // region Contact mappings

    @Query("SELECT * FROM contact_mapping")
    suspend fun getAllMappings(): List<ContactMappingEntity>

    @Query("SELECT * FROM contact_mapping")
    fun observeAllMappings(): Flow<List<ContactMappingEntity>>

    @Query("SELECT * FROM contact_mapping WHERE isOnFlipcash = 1")
    fun observeFlipcashContacts(): Flow<List<ContactMappingEntity>>

    @Query("SELECT * FROM contact_mapping WHERE isOnFlipcash = 0")
    fun observeNonFlipcashContacts(): Flow<List<ContactMappingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMappings(mappings: List<ContactMappingEntity>)

    @Query("SELECT displayName FROM contact_mapping WHERE e164 = :e164 LIMIT 1")
    suspend fun getDisplayName(e164: String): String?

    @Query("SELECT photoUri FROM contact_mapping WHERE e164 = :e164 LIMIT 1")
    suspend fun getPhotoUri(e164: String): String?

    @Query("DELETE FROM contact_mapping WHERE e164 IN (:e164s)")
    suspend fun deleteMappings(e164s: List<String>)

    @Query("UPDATE contact_mapping SET isOnFlipcash = 0")
    suspend fun clearFlipcashStatus()

    @Query("UPDATE contact_mapping SET isOnFlipcash = 1 WHERE e164 IN (:e164s)")
    suspend fun markAsFlipcash(e164s: List<String>)

    // endregion

    @Query("DELETE FROM contact_mapping")
    suspend fun deleteAllMappings()

    @Query("UPDATE contact_sync_state SET hasDiscoveredFlipcashContacts = 1 WHERE id = 0")
    suspend fun markFlipcashContactsDiscovered()

    @Query("UPDATE contact_sync_state SET hasDiscoveredFlipcashContacts = 0 WHERE id = 0")
    suspend fun clearFlipcashContactsDiscovered()

    @Transaction
    suspend fun clearAll() {
        clearSyncState()
        deleteAllMappings()
    }
}
