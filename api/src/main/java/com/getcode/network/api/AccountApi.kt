package com.getcode.network.api

import com.codeinc.gen.account.v1.AccountGrpc
import com.codeinc.gen.account.v1.AccountService
import com.getcode.network.core.GrpcApi
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject


class AccountApi @Inject constructor(
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = AccountGrpc.newStub(managedChannel)

    fun isCodeAccount(request: AccountService.IsCodeAccountRequest): Single<AccountService.IsCodeAccountResponse> {
        return api::isCodeAccount
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getTokenAccountInfos(request: AccountService.GetTokenAccountInfosRequest): Single<AccountService.GetTokenAccountInfosResponse> {
        return api::getTokenAccountInfos
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }
}