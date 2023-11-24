package com.getcode.network.api

import com.codeinc.gen.user.v1.IdentityGrpc
import com.codeinc.gen.user.v1.IdentityService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

class IdentityApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = IdentityGrpc.newStub(managedChannel)

    fun linkAccount(request: IdentityService.LinkAccountRequest): @NonNull Single<IdentityService.LinkAccountResponse> {
        return api::linkAccount
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun unlinkAccount(request: IdentityService.UnlinkAccountRequest): @NonNull Single<IdentityService.UnlinkAccountResponse> {
        return api::unlinkAccount
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getUser(request: IdentityService.GetUserRequest): @NonNull Single<IdentityService.GetUserResponse> {
        return api::getUser
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}
