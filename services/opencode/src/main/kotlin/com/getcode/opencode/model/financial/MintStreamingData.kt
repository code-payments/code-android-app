package com.getcode.opencode.model.financial

import com.getcode.solana.keys.Signature
import kotlin.time.Instant

sealed interface VerifiedResponseData {
    data class ExchangeRate(
        val rate: Rate,
        val timestamp: Instant,
        val signature: Signature,
    ): VerifiedResponseData

    data class LaunchpadReserveState(
        val reserveState: LaunchpadReserveStateSnapshot,
        val signature: Signature,
    ): VerifiedResponseData
}

sealed interface LiveMintDataResponse {
    data class ExchangeRates(
        val rates: List<VerifiedResponseData.ExchangeRate>
    ): LiveMintDataResponse
    data class LaunchpadReserveState(
        val reserveStates: List<VerifiedResponseData.LaunchpadReserveState>,
    ): LiveMintDataResponse
}
