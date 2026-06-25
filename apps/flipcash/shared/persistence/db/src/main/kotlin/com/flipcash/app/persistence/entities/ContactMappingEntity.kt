package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_mapping")
data class ContactMappingEntity(
    @PrimaryKey
    val e164: String,
    val androidContactId: Long,
    val displayName: String,
    val photoUri: String?,
    val isOnFlipcash: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val displayNumber: String = "",
    @ColumnInfo(defaultValue = "")
    val dmChatId: String = "",
    @ColumnInfo(defaultValue = "0")
    val joinedAtEpochSeconds: Long = 0L,
)
