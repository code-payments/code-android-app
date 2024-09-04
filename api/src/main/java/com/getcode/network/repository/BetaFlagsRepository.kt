package com.getcode.network.repository

import com.getcode.model.PrefsBool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class BetaOptions(
    val showNetworkDropOff: Boolean,
    val canViewBuckets: Boolean,
    val tickOnScan: Boolean,
    val debugScanTimesEnabled: Boolean,
    val displayErrors: Boolean,
    val giveRequestsEnabled: Boolean,
    val buyModuleEnabled: Boolean,
    val chatUnsubEnabled: Boolean,
    val tipsEnabled: Boolean,
    val conversationsEnabled: Boolean,
    val conversationCashEnabled: Boolean,
    val balanceCurrencySelectionEnabled: Boolean,
    val kadoWebViewEnabled: Boolean,
    val shareTweetToTip: Boolean,
    val tipCardOnHomeScreen: Boolean,
    val cameraGesturesEnabled: Boolean,
    val invertedDragZoom: Boolean,
    val canFlipTipCard: Boolean,
    val galleryEnabled: Boolean,
) {
    companion object {
        // Default states for various beta flags in app.
        val Defaults = BetaOptions(
            showNetworkDropOff = false,
            canViewBuckets = false,
            tickOnScan = false,
            debugScanTimesEnabled = false,
            displayErrors = false,
            giveRequestsEnabled = false,
            buyModuleEnabled = true,
            chatUnsubEnabled = false,
            tipsEnabled = true,
            conversationsEnabled = false,
            conversationCashEnabled = false,
            balanceCurrencySelectionEnabled = true,
            kadoWebViewEnabled = false,
            shareTweetToTip = true,
            tipCardOnHomeScreen = true,
            cameraGesturesEnabled = true,
            invertedDragZoom = false,
            canFlipTipCard = false,
            galleryEnabled = false
        )
    }
}

class BetaFlagsRepository @Inject constructor(
    private val prefRepository: PrefRepository,
) {
    suspend fun isEnabled() = prefRepository.get(PrefsBool.IS_DEBUG_ALLOWED, false)

    fun enableBeta(allowed: Boolean) {
        prefRepository.set(
            PrefsBool.IS_DEBUG_ALLOWED,
            allowed,
        )

        if (!allowed) {
            prefRepository.set(PrefsBool.IS_DEBUG_ACTIVE, false)
        }
    }

    fun observe(): Flow<BetaOptions> = BetaOptions.Defaults.let { defaults ->
        combine(
            observeBetaFlag(PrefsBool.SHOW_CONNECTIVITY_STATUS, default = defaults.showNetworkDropOff),
            observeBetaFlag(PrefsBool.BUCKET_DEBUGGER_ENABLED,  default = defaults.canViewBuckets),
            observeBetaFlag(PrefsBool.VIBRATE_ON_SCAN, default = defaults.tickOnScan),
            observeBetaFlag(PrefsBool.LOG_SCAN_TIMES, default = defaults.debugScanTimesEnabled),
            observeBetaFlag(PrefsBool.GIVE_REQUESTS_ENABLED, default = defaults.giveRequestsEnabled),
            observeBetaFlag(PrefsBool.BUY_MODULE_ENABLED, default = defaults.buyModuleEnabled),
            observeBetaFlag(PrefsBool.CHAT_UNSUB_ENABLED, default = defaults.chatUnsubEnabled),
            observeBetaFlag(PrefsBool.TIPS_ENABLED, default = defaults.tipsEnabled),
            observeBetaFlag(PrefsBool.CONVERSATIONS_ENABLED, default = defaults.conversationsEnabled),
            observeBetaFlag(PrefsBool.CONVERSATION_CASH_ENABLED, default = defaults.conversationCashEnabled),
            observeBetaFlag(PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED, defaults.balanceCurrencySelectionEnabled),
            observeBetaFlag(PrefsBool.DISPLAY_ERRORS, default = defaults.displayErrors),
            observeBetaFlag(PrefsBool.KADO_WEBVIEW_ENABLED, default = defaults.kadoWebViewEnabled),
            observeBetaFlag(PrefsBool.SHARE_TWEET_TO_TIP, default = defaults.shareTweetToTip),
            observeBetaFlag(PrefsBool.TIP_CARD_ON_HOMESCREEN, defaults.tipCardOnHomeScreen),
            observeBetaFlag(PrefsBool.CAMERA_GESTURES_ENABLED, defaults.cameraGesturesEnabled),
            observeBetaFlag(PrefsBool.CAMERA_DRAG_INVERTED, defaults.invertedDragZoom),
            observeBetaFlag(PrefsBool.TIP_CARD_FLIPPABLE, defaults.canFlipTipCard),
            observeBetaFlag(PrefsBool.GALLERY_ENABLED, defaults.galleryEnabled),
        ) {
            BetaOptions(
                showNetworkDropOff = it[0],
                canViewBuckets = it[1],
                tickOnScan = it[2],
                debugScanTimesEnabled = it[3],
                giveRequestsEnabled = it[4],
                buyModuleEnabled = it[5],
                chatUnsubEnabled = it[6],
                tipsEnabled = it[7],
                conversationsEnabled = it[8],
                conversationCashEnabled = it[9],
                balanceCurrencySelectionEnabled = it[10],
                displayErrors = it[11],
                kadoWebViewEnabled = it[12],
                shareTweetToTip = it[13],
                tipCardOnHomeScreen = it[14],
                cameraGesturesEnabled = it[15],
                invertedDragZoom = it[16],
                canFlipTipCard = it[17],
                galleryEnabled = it[18],
            )
        }
    }

    private fun observeBetaFlag(flag: PrefsBool, default: Boolean = false): Flow<Boolean> {
        return combine(
            prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_ALLOWED, false),
            prefRepository.observeOrDefault(flag, default)
        ) { a, b ->
            b.takeIf { a } ?: default
        }
    }

    suspend fun isEnabled(flag: PrefsBool): Boolean {
        return prefRepository.get(flag, default(flag))
    }

    private fun default(flag: PrefsBool): Boolean {
        return with(BetaOptions.Defaults) {
            when (flag) {
                PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED -> balanceCurrencySelectionEnabled
                PrefsBool.BUCKET_DEBUGGER_ENABLED -> canViewBuckets
                PrefsBool.BUY_MODULE_ENABLED -> buyModuleEnabled
                PrefsBool.CAMERA_GESTURES_ENABLED -> cameraGesturesEnabled
                PrefsBool.CAMERA_DRAG_INVERTED -> invertedDragZoom
                PrefsBool.CHAT_UNSUB_ENABLED -> chatUnsubEnabled
                PrefsBool.CONVERSATIONS_ENABLED -> conversationsEnabled
                PrefsBool.CONVERSATION_CASH_ENABLED -> conversationCashEnabled
                PrefsBool.DISPLAY_ERRORS -> displayErrors
                PrefsBool.GALLERY_ENABLED -> galleryEnabled
                PrefsBool.GIVE_REQUESTS_ENABLED -> giveRequestsEnabled
                PrefsBool.KADO_WEBVIEW_ENABLED -> kadoWebViewEnabled
                PrefsBool.LOG_SCAN_TIMES -> debugScanTimesEnabled
                PrefsBool.SHARE_TWEET_TO_TIP -> shareTweetToTip
                PrefsBool.SHOW_CONNECTIVITY_STATUS -> showNetworkDropOff
                PrefsBool.TIPS_ENABLED -> tipsEnabled
                PrefsBool.TIP_CARD_FLIPPABLE -> canFlipTipCard
                PrefsBool.TIP_CARD_ON_HOMESCREEN -> tipCardOnHomeScreen
                PrefsBool.VIBRATE_ON_SCAN -> tickOnScan
                PrefsBool.BUY_MODULE_AVAILABLE -> false
                PrefsBool.CAMERA_START_BY_DEFAULT -> false
                PrefsBool.DISMISSED_TIP_CARD_BANNER -> false
                PrefsBool.ESTABLISH_CODE_RELATIONSHIP -> false
                PrefsBool.HAS_REMOVED_LOCAL_CURRENCY -> false
                PrefsBool.IS_DEBUG_ACTIVE -> false
                PrefsBool.IS_DEBUG_ALLOWED -> false
                PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP -> false
                PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP -> false
                PrefsBool.REQUIRE_BIOMETRICS -> false
                PrefsBool.SEEN_TIP_CARD -> false
                PrefsBool.STARTED_TIP_CONNECT -> false
            }
        }
    }
}
