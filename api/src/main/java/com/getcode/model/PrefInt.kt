package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefInt(
    @PrimaryKey val key: String,
    val value: Long
)