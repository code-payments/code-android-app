package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefBool(
    @PrimaryKey val key: String,
    val value: Boolean
)

enum class PrefsBool(val value: String) {
    IS_DEBUG_ACTIVE("debug_menu_active"),
    IS_DEBUG_ALLOWED("debug_menu_allowed"),
    IS_DEBUG_BUCKETS("debug_buckets"),
    IS_DEBUG_VIBRATE_ON_SCAN("vibrate_on_scan"),
    IS_DEBUG_DISPLAY_ERRORS("debug_display_errors"),
    IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP("is_eligible_get_first_kin_airdrop"),
    IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP("is_eligible_give_first_kin_airdrop")
}