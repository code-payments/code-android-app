package com.getcode.network.repository

import com.getcode.model.PrefsBool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class AccountDebugSettings(
    val showNetworkDropOff: Boolean = false,
    val canViewBuckets: Boolean = false,
    val tickOnScan: Boolean = false,
    val debugScanTimesEnabled: Boolean = false,
    val displayErrors: Boolean = false,
    val remoteSendEnabled: Boolean = false,
    val incentivesEnabled: Boolean = false,
)

class AccountDebugRepository @Inject constructor(
    private val prefRepository: PrefRepository,
) {

    fun observe(): Flow<AccountDebugSettings> = combine(
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_NETWORK_NO_CONNECTION, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_BUCKETS, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_SCAN_TIMES, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_DISPLAY_ERRORS, false),
    ) { network, buckets, vibez, times, errors ->
        AccountDebugSettings(
            showNetworkDropOff = network,
            canViewBuckets = buckets,
            tickOnScan = vibez,
            debugScanTimesEnabled = times,
            displayErrors = errors
        )
    }
}