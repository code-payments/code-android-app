package com.getcode.network.repository

import com.getcode.model.BetaFlags
import com.getcode.model.PrefsBool
import com.getcode.utils.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class BetaOptions(
    val showNetworkDropOff: Boolean = false,
    val canViewBuckets: Boolean = false,
    val tickOnScan: Boolean = false,
    val debugScanTimesEnabled: Boolean = false,
    val displayErrors: Boolean = false,
    val remoteSendEnabled: Boolean = false,
    val giveRequestsEnabled: Boolean = false,
    val buyKinEnabled: Boolean = false,
)

class BetaFlagsRepository @Inject constructor(
    private val prefRepository: PrefRepository,
) {

    fun observe(): Flow<BetaOptions> = combine(
        observeBetaFlag(PrefsBool.SHOW_CONNECTIVITY_STATUS),
        observeBetaFlag(PrefsBool.BUCKET_DEBUGGER_ENABLED),
        observeBetaFlag(PrefsBool.VIBRATE_ON_SCAN),
        observeBetaFlag(PrefsBool.LOG_SCAN_TIMES),
        observeBetaFlag(PrefsBool.GIVE_REQUESTS_ENABLED),
        observeBetaFlag(PrefsBool.BUY_KIN_ENABLED),
        observeBetaFlag(PrefsBool.DISPLAY_ERRORS),
    ) { network, buckets, vibez, times, giveRequests, buyKin, errors ->
        BetaOptions(
            showNetworkDropOff = network,
            canViewBuckets = buckets,
            tickOnScan = vibez,
            debugScanTimesEnabled = times,
            giveRequestsEnabled = giveRequests,
            buyKinEnabled = buyKin,
            displayErrors = errors
        )
    }

    private fun observeBetaFlag(flag: PrefsBool, default: Boolean = false): Flow<Boolean> {
        return prefRepository.observeOrDefault(flag, default)
            .map {
                if (BetaFlags.isAvailable(flag)) {
                    it
                } else {
                    false
                }
            }
    }
}