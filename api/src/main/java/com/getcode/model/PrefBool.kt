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
    data object ESTABLISH_CODE_RELATIONSHIP : PrefsBool("establish_code_relationship_enabled")
    data object CHAT_UNSUB_ENABLED: PrefsBool("chat_unsub_enabled")
    data object TIPS_ENABLED : PrefsBool("tips_enabled")
}

object BetaFlags {
    /**
     * Override to disable beta flags in app while WIP.
     */
    fun isAvailable(flag: PrefsBool): Boolean {
        return true
    }

    /**
     * Override to disabling UI interaction for beta flags.
     */
    fun canMutate(flag: PrefsBool): Boolean {
        return when (flag) {
            PrefsBool.BUY_KIN_ENABLED -> true
            else -> true
        }
    }
}