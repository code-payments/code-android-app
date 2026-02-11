package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.currency.v1.CurrencyGrpcKt
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeManagedStreamingChannel
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CurrencyApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,

    @OpenCodeManagedStreamingChannel
    streamingChannel: ManagedChannel,
) : GrpcApi(managedChannel, streamingChannel) {

    private val api = CurrencyGrpcKt.CurrencyCoroutineStub(managedChannel)
        .withWaitForReady()

    private val streamingApi = CurrencyGrpcKt.CurrencyCoroutineStub(streamingChannel)
        .withWaitForReady()

    /**
     * Gets mint account metadata by address
     *
     * @param mintAddresses The list of mint addresses to query
     *
     * @return The [CurrencyService.GetMintsResponse] with mint account metadata by address
     */
    suspend fun getMints(
        mintAddresses: List<PublicKey>
    ): CurrencyService.GetMintsResponse {
        val request = CurrencyService.GetMintsRequest.newBuilder()
            .apply {
                mintAddresses.forEachIndexed { index, address ->
                    addAddresses(index, address.asSolanaAccountId())
                }
            }.build()

        return withContext(Dispatchers.IO) {
            api.getMints(request)
        }
    }

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): CurrencyService.GetHistoricalMintDataResponse {
        val request = CurrencyService.GetHistoricalMintDataRequest.newBuilder()
            .setAddress(mint.asSolanaAccountId())
            .setCurrencyCode(currencyCode.name.lowercase())
            .setPredefinedRange(
                when (windowedRange) {
                    WindowedRange.AllTime -> CurrencyService.GetHistoricalMintDataRequest.PredefinedRange.ALL_TIME
                    WindowedRange.LastDay -> CurrencyService.GetHistoricalMintDataRequest.PredefinedRange.LAST_DAY
                    WindowedRange.LastWeek -> CurrencyService.GetHistoricalMintDataRequest.PredefinedRange.LAST_WEEK
                    WindowedRange.LastMonth -> CurrencyService.GetHistoricalMintDataRequest.PredefinedRange.LAST_MONTH
                    WindowedRange.LastYear -> CurrencyService.GetHistoricalMintDataRequest.PredefinedRange.LAST_YEAR
                }
            ).build()

        return withContext(Dispatchers.IO) {
            api.getHistoricalMintData(request)
        }
    }

    fun streamLiveMintData(
        requests: Flow<CurrencyService.StreamLiveMintDataRequest>
    ): Flow<CurrencyService.StreamLiveMintDataResponse> {
        return streamingApi.streamLiveMintData(requests)
    }
}