package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefString(
    @PrimaryKey val key: String,
    val value: String
)

enum class PrefsString(val value: String) {
    KEY_USER_ID("user_id"),
    KEY_DATA_CONTAINER_ID("data_container_id"),
    KEY_CURRENCY_SELECTED("currency_selected"),//keep
    KEY_CURRENCIES_RECENT("currencies_recent"),//keep
}