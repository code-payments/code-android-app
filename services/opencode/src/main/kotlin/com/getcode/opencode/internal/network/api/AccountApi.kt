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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccountApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = AccountGrpcKt.AccountCoroutineStub(managedChannel)

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
     * @param accountOwner The owner account to fetch balances for, which can also be thought of as a
     *   parent account for this RPC that links to one or more token accounts.
     * @param requestingOwner A requesting owner account that is requesting the balance for owner. Additional
     *   metadata that is considered private will be provided, if applicable. An example
     *   use case includes a user owner account requesting account info for a gift card
     *   owner account.
     *
     * @return The [AccountService.GetTokenAccountInfosResponse] for the owner account.
     */
    suspend fun getTokenAccounts(
        accountOwner: KeyPair,
        requestingOwner: KeyPair,
    ): AccountService.GetTokenAccountInfosResponse {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(accountOwner.asSolanaAccountId())
            .setRequestingOwner(requestingOwner.asSolanaAccountId())
            .apply {
                val ownerSig = sign(accountOwner)
                val requestingOwnerSig = sign(requestingOwner)
                setSignature(ownerSig)
                setRequestingOwnerSignature(requestingOwnerSig)
            }
            .build()

        return withContext(Dispatchers.IO) {
            api.getTokenAccountInfos(request)
        }
    }
}