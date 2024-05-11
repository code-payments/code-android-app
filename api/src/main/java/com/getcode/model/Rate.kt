package com.getcode.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable


@Serializable
data class Rate(
    val fx: Double,
    val currency: CurrencyCode
) {
    companion object {
        val oneToOne = Rate(fx = 1.0, currency = CurrencyCode.KIN)
    }
}

fun Rate?.orOneToOne() = this ?: Rate.oneToOne

@Serializable
@Entity(tableName = "exchangeData")
data class ExchangeRate(
    @ColumnInfo(name = "fiat")
    val fx: Double,
    @PrimaryKey
    val currency: CurrencyCode,
    @ColumnInfo(name = "synced_at")
    val synced: Long,
)