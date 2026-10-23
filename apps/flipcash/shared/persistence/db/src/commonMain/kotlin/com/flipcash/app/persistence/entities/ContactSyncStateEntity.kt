package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_sync_state")
data class ContactSyncStateEntity(
    @PrimaryKey
    val id: Int = 0,
    val checksumBytes: ByteArray,
    val lastSyncTimestamp: Long,
    val needsFullUpload: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val hasDiscoveredFlipcashContacts: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactSyncStateEntity) return false
        return id == other.id &&
            checksumBytes.contentEquals(other.checksumBytes) &&
            lastSyncTimestamp == other.lastSyncTimestamp &&
            needsFullUpload == other.needsFullUpload &&
            hasDiscoveredFlipcashContacts == other.hasDiscoveredFlipcashContacts
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + checksumBytes.contentHashCode()
        result = 31 * result + lastSyncTimestamp.hashCode()
        result = 31 * result + needsFullUpload.hashCode()
        result = 31 * result + hasDiscoveredFlipcashContacts.hashCode()
        return result
    }
}
