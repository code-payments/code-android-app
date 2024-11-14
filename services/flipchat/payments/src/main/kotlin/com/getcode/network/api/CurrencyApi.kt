package com.getcode.network.api

import com.codeinc.gen.currency.v1.CurrencyGrpc
import com.codeinc.gen.currency.v1.CodeCurrencyService as CurrencyService
import com.getcode.services.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import javax.inject.Inject

class CurrencyApi @Inject constructor(
    @PaymentsManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = CurrencyGrpc.newStub(managedChannel).withWaitForReady()

    fun getRates(request: CurrencyService.GetAllRatesRequest = CurrencyService.GetAllRatesRequest.getDefaultInstance()): Flow<CurrencyService.GetAllRatesResponse> =
        api::getAllRates
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
}
