package com.getcode.network.api

import com.codeinc.gen.push.v1.PushGrpc
import com.codeinc.gen.push.v1.PushService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class PushApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = PushGrpc.newStub(managedChannel)

    fun addToken(request: PushService.AddTokenRequest): @NonNull Single<PushService.AddTokenResponse> {
        return api::addToken
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}
