@file:Suppress("ClassName")

package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PrefBool(
    @PrimaryKey val key: String,
    val value: Boolean
)

// Used internally to control logic and UI
sealed interface InternalRouting
// User setting exposed in Settings -> App Settings
sealed interface AppSetting
// Beta flag exposed in Settings -> Beta Flags to enable bleeding edge features
sealed interface BetaFlag
// Dev settings
sealed interface DevSetting
// Once a feature behind a beta flag is made public, it becomes immutable
// This removes it from the UI in Settings -> Beta Flags
sealed interface Immutable


sealed class PrefsBool(val value: String) {
    // internal routing
    data object IS_DEBUG_ACTIVE: PrefsBool("debug_menu_active"), InternalRouting
    data object IS_DEBUG_ALLOWED: PrefsBool("debug_menu_allowed"), InternalRouting
    data object IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP: PrefsBool("is_eligible_get_first_kin_airdrop"), InternalRouting
    data object IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP: PrefsBool("is_eligible_give_first_kin_airdrop"), InternalRouting
    data object HAS_REMOVED_LOCAL_CURRENCY: PrefsBool("removed_local_currency"), InternalRouting
    data object DISMISSED_TIP_CARD_BANNER : PrefsBool("dismissed_tip_card_banner"), InternalRouting
    data object SEEN_TIP_CARD : PrefsBool("seen_tip_card"), InternalRouting
    data object STARTED_TIP_CONNECT: PrefsBool("started_tip_connect"), InternalRouting

    data object BUY_MODULE_AVAILABLE : PrefsBool("buy_module_available"), InternalRouting

    // app settings
    data object CAMERA_START_BY_DEFAULT: PrefsBool("camera_start_default"), AppSetting
    data object REQUIRE_BIOMETRICS: PrefsBool("require_biometrics"), AppSetting

    // dev settings
    data object ESTABLISH_CODE_RELATIONSHIP : PrefsBool("establish_code_relationship_enabled"), DevSetting

    // beta flags
    data object BUCKET_DEBUGGER_ENABLED: PrefsBool("debug_buckets"), BetaFlag
    data object VIBRATE_ON_SCAN: PrefsBool("vibrate_on_scan"), BetaFlag
    data object LOG_SCAN_TIMES: PrefsBool("debug_scan_times"), BetaFlag
    data object DISPLAY_ERRORS: PrefsBool("debug_display_errors"), BetaFlag
    data object SHOW_CONNECTIVITY_STATUS: PrefsBool("debug_no_network"), BetaFlag
    data object GIVE_REQUESTS_ENABLED: PrefsBool("give_requests_enabled"), BetaFlag
    data object BUY_MODULE_ENABLED : PrefsBool("buy_kin_enabled"), BetaFlag, Immutable
    data object CHAT_UNSUB_ENABLED: PrefsBool("chat_unsub_enabled"), BetaFlag
    data object TIPS_ENABLED : PrefsBool("tips_enabled"), BetaFlag, Immutable
    data object CONVERSATIONS_ENABLED: PrefsBool("conversations_enabled"), BetaFlag
    data object CONVERSATION_CASH_ENABLED: PrefsBool("convo_cash_enabled"), BetaFlag
    data object BALANCE_CURRENCY_SELECTION_ENABLED: PrefsBool("balance_currency_enabled"), BetaFlag, Immutable
    data object KADO_WEBVIEW_ENABLED : PrefsBool("kado_inapp_enabled"), BetaFlag
    data object SHARE_TWEET_TO_TIP : PrefsBool("share_tweet_to_tip"), BetaFlag, Immutable
    data object TIP_CARD_ON_HOMESCREEN: PrefsBool("tip_card_on_home_screen"), BetaFlag, Immutable
    data object TIP_CARD_FLIPPABLE: PrefsBool("tipcard_flippable"), BetaFlag
    data object CAMERA_GESTURES_ENABLED: PrefsBool("camera_gestures_enabled"), BetaFlag
    data object GALLERY_ENABLED: PrefsBool("gallery_enabled"), BetaFlag
}

val APP_SETTINGS: List<AppSetting> = listOf(PrefsBool.CAMERA_START_BY_DEFAULT, PrefsBool.REQUIRE_BIOMETRICS)