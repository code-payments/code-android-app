package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.account.v1.AccountGrpcKt
import com.codeinc.opencode.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class AccountApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = AccountGrpcKt.AccountCoroutineStub(managedChannel).withWaitForReady()

    /**
     * Returns whether an owner account is a Code account. This hints
     * to the client whether the account can be logged in, used for making payments,
     * etc.
     *
     * @param owner The owner account to check against.
     * @return The [AccountService.IsCodeAccountResponse]
     */
    suspend fun isCodeAccount(
        owner: KeyPair,
    ): AccountService.IsCodeAccountResponse {
        val request = AccountService.IsCodeAccountRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.isCodeAccount(request)
        }
    }

    /**
     * Returns token account metadata relevant to the Code owner
     * account.
     *
     * @param owner The owner account, which can also be thought of as a parent account for this
     *   RPC that links to one or more token accounts.
     *
     * @return The [AccountService.GetTokenAccountInfosResponse]
     */
    suspend fun getTokenAccounts(
        owner: KeyPair
    ): AccountService.GetTokenAccountInfosResponse {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getTokenAccountInfos(request)
        }
    }
}