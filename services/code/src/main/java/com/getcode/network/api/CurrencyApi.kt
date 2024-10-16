package com.getcode.network.api

import com.codeinc.gen.currency.v1.CurrencyGrpc
import com.codeinc.gen.currency.v1.CurrencyService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class CurrencyApi @Inject constructor(
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = CurrencyGrpc.newStub(managedChannel).withWaitForReady()

    fun getRates(request: CurrencyService.GetAllRatesRequest = CurrencyService.GetAllRatesRequest.getDefaultInstance()): Flow<CurrencyService.GetAllRatesResponse> =
        api::getAllRates
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
}
