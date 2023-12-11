package com.getcode.network.repository

import com.getcode.model.PrefsBool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class AccountDebugSettings(
    val isDebugBuckets: Boolean = false,
    val isVibrateOnScan: Boolean = false,
    val isDisplayErrors: Boolean = false,
    val isRemoteSendEnabled: Boolean = false,
    val isIncentivesEnabled: Boolean = false,
)

class AccountDebugRepository @Inject constructor(
    private val prefRepository: PrefRepository,
) {

    fun observe(): Flow<AccountDebugSettings> = combine(
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_BUCKETS, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, false),
        prefRepository.observeOrDefault(PrefsBool.IS_DEBUG_DISPLAY_ERRORS, false),
    ) { buckets, vibez, errors ->
        AccountDebugSettings(
            isDebugBuckets = buckets,
            isVibrateOnScan = vibez,
            isDisplayErrors = errors
        )
    }
}