@file:Suppress("ClassName")

package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefBool(
    @PrimaryKey val key: String,
    val value: Boolean
)


sealed interface BetaFlag
sealed class PrefsBool(val value: String) {
    // internal routing
    data object IS_DEBUG_ACTIVE: PrefsBool("debug_menu_active")
    data object IS_DEBUG_ALLOWED: PrefsBool("debug_menu_allowed")
    data object IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP: PrefsBool("is_eligible_get_first_kin_airdrop")
    data object IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP: PrefsBool("is_eligible_give_first_kin_airdrop")
    data object HAS_REMOVED_LOCAL_CURRENCY: PrefsBool("removed_local_currency")

    // beta flags
    data object BUCKET_DEBUGGER_ENABLED: PrefsBool("debug_buckets"), BetaFlag
    data object VIBRATE_ON_SCAN: PrefsBool("vibrate_on_scan"), BetaFlag
    data object LOG_SCAN_TIMES: PrefsBool("debug_scan_times"), BetaFlag
    data object DISPLAY_ERRORS: PrefsBool("debug_display_errors"), BetaFlag
    data object SHOW_CONNECTIVITY_STATUS: PrefsBool("debug_no_network"), BetaFlag
    data object GIVE_REQUESTS_ENABLED: PrefsBool("give_requests_enabled"), BetaFlag
    data object BUY_KIN_ENABLED : PrefsBool("buy_kin_enabled"), BetaFlag
    data object REQUEST_PAYMENTS_V2 : PrefsBool("request_payments_v2"), BetaFlag
}

object BetaFlags {
    /**
     * Override to disable beta flags in app while WIP.
     */
    fun isAvailable(flag: PrefsBool): Boolean {
        return true
    }
}