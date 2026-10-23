package com.flipcash.app.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "token_valuation",
    foreignKeys = [
        ForeignKey(
            entity = TokenEntity::class,
            parentColumns = ["address"],
            childColumns = ["token_address"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("token_address")]
)
data class TokenValuationEntity(
    @PrimaryKey
    @ColumnInfo(name = "token_address")
    val tokenAddress: String,

    @ColumnInfo("balance_quarks")
    val balanceQuarks: Long,

    @ColumnInfo("cost_basis")
    val costBasis: Double,
)
