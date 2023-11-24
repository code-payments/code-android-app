package com.getcode.network.api

import com.codeinc.gen.currency.v1.CurrencyGrpc
import com.codeinc.gen.currency.v1.CurrencyService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class CurrencyApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io()
) : GrpcApi(managedChannel) {
    private val api = CurrencyGrpc.newStub(managedChannel)

    fun getRates(request: CurrencyService.GetAllRatesRequest = CurrencyService.GetAllRatesRequest.getDefaultInstance()): Single<CurrencyService.GetAllRatesResponse> =
        api::getAllRates
            .callAsSingle(request)
            .subscribeOn(scheduler)
}
