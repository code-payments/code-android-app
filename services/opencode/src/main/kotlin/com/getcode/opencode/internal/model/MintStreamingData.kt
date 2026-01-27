package com.getcode.opencode.internal.model

import com.getcode.opencode.model.financial.LaunchpadReserveStateSnapshot
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Signature

sealed interface VerifiedResponseData {
    data class ExchangeRate(
        val rate: Rate,
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
