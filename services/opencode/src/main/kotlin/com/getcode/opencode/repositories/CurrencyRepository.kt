package com.getcode.opencode.repositories

import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.internal.network.streamers.OcpMintStreamingReference
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant

interface CurrencyRepository {
    fun streamMintData(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String? = null,
        onUpdate: (LiveMintDataResponse) -> Unit,
    ): OcpMintStreamingReference

    suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>>

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange
    ): Result<List<HistoricalMintData>>
}