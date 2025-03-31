package com.getcode.network.api

import com.codeinc.gen.account.v1.AccountGrpc
import com.codeinc.gen.account.v1.CodeAccountService as AccountService
import com.codeinc.gen.account.v1.CodeAccountService.LinkAdditionalAccountsRequest
import com.codeinc.gen.account.v1.CodeAccountService.LinkAdditionalAccountsResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.repository.toSolanaAccount
import com.getcode.services.network.core.GrpcApi
import com.getcode.utils.sign
import io.grpc.ManagedChannel
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import xyz.flipchat.services.internal.annotations.PaymentsManagedChannel
import javax.inject.Inject


class AccountApi @Inject constructor(
    @PaymentsManagedChannel
    managedChannel: ManagedChannel,
    private val scheduler: Scheduler = Schedulers.io(),
) : GrpcApi(managedChannel) {
    private val api = AccountGrpc.newStub(managedChannel).withWaitForReady()

    fun isCodeAccount(owner: KeyPair): Flow<AccountService.IsCodeAccountResponse> {
        val request = AccountService.IsCodeAccountRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::isCodeAccount
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun getTokenAccountInfos(request: AccountService.GetTokenAccountInfosRequest): Single<AccountService.GetTokenAccountInfosResponse> {
        return api::getTokenAccountInfos
            .callAsSingle(request)
            .subscribeOn(scheduler)
    }

    fun getTokenAccountInfosFlow(request: AccountService.GetTokenAccountInfosRequest): Flow<AccountService.GetTokenAccountInfosResponse> {
        return api::getTokenAccountInfos
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun linkAdditionalAccounts(owner: KeyPair, linkedAccount: KeyPair): Flow<LinkAdditionalAccountsResponse> {
        val request = LinkAdditionalAccountsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setSwapAuthority(linkedAccount.publicKeyBytes.toSolanaAccount())
            .let { it.addAllSignatures(listOf(it.sign(owner), it.sign(linkedAccount))) }
            .build()

        return api::linkAdditionalAccounts
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}