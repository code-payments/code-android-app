package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefDouble(
    @PrimaryKey val key: String,
    val value: Double
)