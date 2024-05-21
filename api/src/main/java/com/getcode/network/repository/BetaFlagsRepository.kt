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
    val establishCodeRelationship: Boolean,
    val chatUnsubEnabled: Boolean,
    val tipsEnabled: Boolean,
    val tipsChatEnabled: Boolean,
    val tipsChatCashEnabled: Boolean,
    val balanceCurrencySelectionEnabled: Boolean,
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
            tipsEnabled = true,
            tipsChatEnabled = false,
            tipsChatCashEnabled = false,
            balanceCurrencySelectionEnabled = false
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
            observeBetaFlag(PrefsBool.TIPS_CHAT_CASH_ENABLED, default = defaults.tipsChatCashEnabled),
            observeBetaFlag(PrefsBool.BALANCE_CURRENCY_SELECTION_ENABLED, defaults.balanceCurrencySelectionEnabled),
            observeBetaFlag(PrefsBool.DISPLAY_ERRORS, default = defaults.displayErrors),
        ) {
            BetaOptions(
                showNetworkDropOff = it[0],
                canViewBuckets = it[1],
                tickOnScan = it[2],
                debugScanTimesEnabled = it[3],
                giveRequestsEnabled = it[4],
                buyModuleEnabled = it[5],
                establishCodeRelationship = it[6],
                chatUnsubEnabled = it[7],
                tipsEnabled = it[8],
                tipsChatEnabled = it[9],
                tipsChatCashEnabled = it[10],
                balanceCurrencySelectionEnabled = it[11],
                displayErrors = it[12],
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