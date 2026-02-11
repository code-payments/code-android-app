package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.domain.mapping.HistoricalMintDataMapper
import com.getcode.opencode.internal.domain.mapping.LiveMintDataMapper
import com.getcode.opencode.internal.domain.mapping.MintMapper
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.network.streamers.LiveMintDataStreamer
import com.getcode.opencode.internal.network.streamers.ManagedMintStream
import com.getcode.opencode.internal.network.streamers.OcpMintStreamingReference
import com.getcode.opencode.model.core.errors.GetHistoricalMintDataError
import com.getcode.opencode.model.core.errors.GetMintsError
import com.getcode.opencode.model.core.errors.GetRatesError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class CurrencyService @Inject constructor(
    private val api: CurrencyApi,
    private val mintMapper: MintMapper,
    private val historicalMintDataMapper: HistoricalMintDataMapper,
    private val liveMintDataMapper: LiveMintDataMapper,
    private val verifiedStateManager: VerifiedProtoManager,
) {
    suspend fun getMints(
        mintAddresses: List<PublicKey>
    ): Result<List<MintMetadata>> {
        return runCatching {
            api.getMints(mintAddresses)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.GetMintsResponse.Result.OK -> {
                        val mints = response.metadataByAddressMap.values.toList()
                            .map { mintMapper.map(it) }

                        Result.success(mints)
                    }
                    CurrencyService.GetMintsResponse.Result.NOT_FOUND -> Result.failure(GetMintsError.NotFound())
                    CurrencyService.GetMintsResponse.Result.UNRECOGNIZED -> Result.failure(GetMintsError.Unrecognized())
                    else -> Result.failure(GetMintsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetMintsError.Other(cause = cause))
            }
        )
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        return runCatching {
            api.getHistoricalMintData(
                mint = mint,
                currencyCode = currencyCode,
                windowedRange = windowedRange,
            )
        }.foldWithSuppression(
            onSuccess = { response ->
                when (val result = response.result) {
                    CurrencyService.GetHistoricalMintDataResponse.Result.OK -> {
                        val data = response.dataList.map { historicalMintDataMapper.map(it) }
                        Result.success(data)
                    }
                    CurrencyService.GetHistoricalMintDataResponse.Result.NOT_FOUND -> Result.failure(GetHistoricalMintDataError.NotFound())
                    CurrencyService.GetHistoricalMintDataResponse.Result.MISSING_DATA -> Result.failure(GetHistoricalMintDataError.MissingData())
                    CurrencyService.GetHistoricalMintDataResponse.Result.UNRECOGNIZED -> Result.failure(GetHistoricalMintDataError.Unrecognized())
                    else -> Result.failure(GetHistoricalMintDataError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(GetHistoricalMintDataError.Other(cause))
            }
        )
    }

    fun streamLiveMintData(
        tag: String?,
        scope: CoroutineScope,
        mints: List<Mint>,
        onUpdate: (LiveMintDataResponse) -> Unit
    ): ManagedMintStream {
        val streamer = LiveMintDataStreamer(api)
        return streamer.stream(scope = scope, mints = mints, tag = tag,) { update ->
            // save protos for later use
            when (update.typeCase) {
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.CORE_MINT_FIAT_EXCHANGE_RATES -> verifiedStateManager.saveRates(update.coreMintFiatExchangeRates.exchangeRatesList)
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.LAUNCHPAD_CURRENCY_RESERVE_STATES -> verifiedStateManager.saveReserveStates(update.launchpadCurrencyReserveStates.reserveStatesList)
                CurrencyService.StreamLiveMintDataResponse.LiveData.TypeCase.TYPE_NOT_SET -> Unit
            }

            // map to domain models for use throughout app (above server)
            val data = liveMintDataMapper.map(update)
            if (data != null) {
                onUpdate(data)
            }
        }
    }
}