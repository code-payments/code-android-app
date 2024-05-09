package com.getcode.network.repository

import com.getcode.model.PrefsBool
import com.getcode.utils.combine
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
    val establishCodeRelationship: Boolean,
    val chatUnsubEnabled: Boolean,
    val tipsEnabled: Boolean,
    val tipsChatEnabled: Boolean,
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
            establishCodeRelationship = false,
            chatUnsubEnabled = false,
            tipsEnabled = false,
            tipsChatEnabled = false
        )
    }
}

class BetaFlagsRepository @Inject constructor(
    private val prefRepository: PrefRepository,
) {
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
            observeBetaFlag(PrefsBool.ESTABLISH_CODE_RELATIONSHIP, default = defaults.establishCodeRelationship),
            observeBetaFlag(PrefsBool.CHAT_UNSUB_ENABLED, default = defaults.chatUnsubEnabled),
            observeBetaFlag(PrefsBool.TIPS_ENABLED, default = defaults.tipsEnabled),
            observeBetaFlag(PrefsBool.TIPS_CHAT_ENABLED, default = defaults.tipsChatEnabled),
            observeBetaFlag(PrefsBool.DISPLAY_ERRORS, default = defaults.displayErrors),
        ) { network, buckets, vibez, times, giveRequests, buyKin, relationship, chatUnsub, tips, tipsChat, errors ->
            BetaOptions(
                showNetworkDropOff = network,
                canViewBuckets = buckets,
                tickOnScan = vibez,
                debugScanTimesEnabled = times,
                giveRequestsEnabled = giveRequests,
                buyModuleEnabled = buyKin,
                establishCodeRelationship = relationship,
                chatUnsubEnabled = chatUnsub,
                tipsEnabled = tips,
                tipsChatEnabled = tipsChat,
                displayErrors = errors
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
}