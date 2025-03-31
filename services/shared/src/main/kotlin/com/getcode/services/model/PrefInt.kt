package com.getcode.services.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefInt(
    @PrimaryKey val key: String,
    val value: Long
)

sealed class PrefsInt(val value: String) {
    data object AccountCreated: PrefsInt("account_created")
}
