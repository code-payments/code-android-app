package com.flipcash.app.persistence.entities

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
)
