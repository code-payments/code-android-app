package com.flipcash.app.persistence.sources

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.entities.ContactMappingEntity
import com.flipcash.app.persistence.entities.ContactSyncStateEntity
import com.flipcash.services.persistence.SingularDataSource
import com.getcode.solana.keys.Checksum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactDataSource @Inject constructor(
) : SingularDataSource<String, ContactMappingEntity, List<ContactMappingEntity>> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    // region SingularDataSource

    override suspend fun getById(id: String): ContactMappingEntity? =
        get().firstOrNull { it.e164 == id }

    override suspend fun get(): List<ContactMappingEntity> =
        db?.contactDao()?.getAllMappings() ?: emptyList()

    override suspend fun upsert(value: List<ContactMappingEntity>) {
        db?.contactDao()?.upsertMappings(value)
    }

    override suspend fun query(whereClause: String): List<ContactMappingEntity> = emptyList()

    override suspend fun getMostRecent(): ContactMappingEntity? = null

    override suspend fun clear() {
        db?.contactDao()?.clearAll()
    }

    override fun observe(): Flow<List<ContactMappingEntity>> =
        db?.contactDao()?.observeAllMappings() ?: emptyFlow()

    override fun observe(id: String): Flow<ContactMappingEntity?> =
        observe().map { mappings -> mappings.firstOrNull { it.e164 == id } }

    // endregion

    // region Sync state

    suspend fun getSyncState(): ContactSyncStateEntity? =
        db?.contactDao()?.getSyncState()

    suspend fun persistSyncState(checksum: Checksum) {
        db?.contactDao()?.upsertSyncState(
            ContactSyncStateEntity(
                checksumBytes = checksum.byteArray,
                lastSyncTimestamp = System.currentTimeMillis(),
            )
        )
    }

    suspend fun hasDiscoveredFlipcashContacts(): Boolean =
        getSyncState()?.hasDiscoveredFlipcashContacts ?: false

    suspend fun markFlipcashContactsDiscovered() {
        db?.contactDao()?.markFlipcashContactsDiscovered()
    }

    suspend fun clearFlipcashContactsDiscovered() {
        db?.contactDao()?.clearFlipcashContactsDiscovered()
    }

    // endregion

    // region Contact-specific operations

    suspend fun deleteMappings(e164s: List<String>) {
        db?.contactDao()?.deleteMappings(e164s)
    }

    suspend fun clearFlipcashStatus() {
        db?.contactDao()?.clearFlipcashStatus()
    }

    suspend fun markAsFlipcash(e164s: List<String>) {
        db?.contactDao()?.markAsFlipcash(e164s)
    }

    suspend fun getDisplayName(e164: String): String? =
        db?.contactDao()?.getDisplayName(e164)

    // endregion
}
