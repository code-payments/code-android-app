package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.internal.network.services.CurrencyService
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

internal class InternalCurrencyRepository @Inject constructor(
    private val service: CurrencyService,
) : CurrencyRepository {

    override fun streamMintData(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String?,
        onUpdate: (LiveMintDataResponse) -> Unit
    ) = service.streamLiveMintData(tag = tag, scope = scope, mints = mints, onUpdate = onUpdate)

    override suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>> =
        service.getMints(addresses)

    override suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange
    ): Result<List<HistoricalMintData>> =
        service.getHistoricalMintData(mint, currencyCode, windowedRange)
}