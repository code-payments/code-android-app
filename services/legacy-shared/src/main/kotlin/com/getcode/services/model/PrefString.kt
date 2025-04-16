package com.getcode.services.model

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
    KEY_ENTRY_CURRENCY("currency_selected"),//keep
    KEY_CURRENCIES_RECENT("currencies_recent"),//keep
    KEY_TIP_ACCOUNT("tip_account"),
    KEY_LOCAL_CURRENCY("balance_currency_selected")
}