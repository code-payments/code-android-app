package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class CurrencyRate(
    @PrimaryKey val id: String,
    val rate: Double
)