package com.getcode.network.api

import com.codeinc.gen.user.v1.IdentityGrpc
import com.codeinc.gen.user.v1.IdentityService
import com.codeinc.gen.user.v1.IdentityService.GetTwitterUserRequest
import com.codeinc.gen.user.v1.IdentityService.LoginToThirdPartyAppRequest
import com.codeinc.gen.user.v1.IdentityService.UpdatePreferencesRequest
import com.getcode.annotations.CodeManagedChannel
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class IdentityApi @Inject constructor(
    @CodeManagedChannel
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = IdentityGrpc.newStub(managedChannel).withWaitForReady()

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

    fun loginToThirdParty(request: LoginToThirdPartyAppRequest) = api::loginToThirdPartyApp
        .callAsCancellableFlow(request)
        .flowOn(Dispatchers.IO)

    fun updatePreferences(request: UpdatePreferencesRequest) = api::updatePreferences
        .callAsCancellableFlow(request)
        .flowOn(Dispatchers.IO)

    fun fetchTwitterUser(request: GetTwitterUserRequest) = api::getTwitterUser
        .callAsCancellableFlow(request)
        .flowOn(Dispatchers.IO)
}
