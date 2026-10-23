package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "currency_creator_draft")
data class CurrencyCreatorDraftEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "icon_uri")
    val iconUri: String?,

    @ColumnInfo(name = "bill_customizations")
    val billCustomizations: String?,

    @ColumnInfo(name = "attestations")
    val attestations: String?,

    @ColumnInfo(name = "current_step")
    val currentStep: String,

    @ColumnInfo(name = "created_mint")
    val createdMint: String?,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
)
