package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.currency.v1.CurrencyGrpc
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asProtobufTimestamp
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
     * Returns the exchange rates for the core mint token against all available currencies
     *
     * @param timestampInMillis If timestamp is included, the returned rate will be the most recent available
     * exchange rate prior to the provided timestamp within the same day. Otherwise,
     * the latest rates will be returned.
     *
     * @return The [CurrencyService.GetAllRatesResponse] with the price of 1 core mint token in
     * different currencies, keyed on 3- or 4-letter lowercase currency code.
     */
    fun getAllRates(
        timestampInMillis: Long?
    ): Flow<CurrencyService.GetAllRatesResponse> {
        val builder = CurrencyService.GetAllRatesRequest.newBuilder()

        if (timestampInMillis != null) {
            builder.setTimestamp(timestampInMillis.asProtobufTimestamp())
        }

        val request = builder.build()

        return api::getAllRates
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}