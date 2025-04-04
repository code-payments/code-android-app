package com.getcode.opencode.internal.network.api

import com.codeinc.gen.currency.v1.CurrencyGrpc
import com.codeinc.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.extensions.toProtobufTimestamp
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class CurrencyApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = CurrencyGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Returns the exchange rates for Kin against all available currencies
     *
     * @param timestampInMillis If timestamp is included, the returned rate will be the most recent available
     * exchange rate prior to the provided timestamp within the same day. Otherwise,
     * the latest rates will be returned.
     *
     * @return The [CurrencyService.GetAllRatesResponse]
     */
    fun getAllRates(
        timestampInMillis: Long?
    ): Flow<CurrencyService.GetAllRatesResponse> {
        val builder = CurrencyService.GetAllRatesRequest.newBuilder()

        if (timestampInMillis != null) {
            builder.setTimestamp(timestampInMillis.toProtobufTimestamp())
        }

        val request = builder.build()

        return api::getAllRates
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}