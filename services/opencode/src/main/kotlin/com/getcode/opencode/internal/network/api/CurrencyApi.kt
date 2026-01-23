package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.currency.v1.CurrencyGrpcKt
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asProtobufTimestamp
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class CurrencyApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = CurrencyGrpcKt.CurrencyCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Returns the exchange rates for the core mint token against all available currencies
     *
     * @param timestampInMillis If timestamp is included, the returned rate will be the most recent available
     * exchange rate prior to the provided timestamp within the same day. Otherwise,
     * the latest rates will be returned.
     *
     * @return The [CurrencyService.GetAllRatesResponse] with the price of 1 core mint token in
     * different currencies, keyed on 3- or 4-letter lowercase currency code.
     */
    suspend fun getAllRates(
        timestampInMillis: Long?
    ): CurrencyService.GetAllRatesResponse {
        val builder = CurrencyService.GetAllRatesRequest.newBuilder()

        if (timestampInMillis != null) {
            builder.setTimestamp(timestampInMillis.asProtobufTimestamp())
        }

        val request = builder.build()

        return withContext(Dispatchers.IO) {
            api.getAllRates(request)
        }
    }

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
}