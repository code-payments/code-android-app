package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.account.v1.AccountGrpc
import com.codeinc.opencode.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class AccountApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = AccountGrpc.newStub(managedChannel).withWaitForReady()

    /**
     * Returns whether an owner account is a Code account. This hints
     * to the client whether the account can be logged in, used for making payments,
     * etc.
     *
     * @param owner The owner account to check against.
     * @return The [AccountService.IsCodeAccountResponse]
     */
    fun isCodeAccount(
        owner: KeyPair,
    ): Flow<AccountService.IsCodeAccountResponse> {
        val request = AccountService.IsCodeAccountRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::isCodeAccount
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
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
    fun getTokenAccounts(
        owner: KeyPair
    ): Flow<AccountService.GetTokenAccountInfosResponse> {
        val request = AccountService.GetTokenAccountInfosRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getTokenAccountInfos
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Allows a client to declare additional accounts to
     * be tracked and used within Code. The accounts declared in this RPC are not
     * managed by Code (ie. not a Timelock account), created externally and cannot
     * be linked automatically (ie. authority derived off user 12 words).
     *
     * @param owner The owner account to link to
     * @param swapAuthority The authority account derived off the user's 12 words, which contains
     * the USDC ATA (and potentially others in the future) that will be used in swaps.
     *
     * @return The [AccountService.LinkAdditionalAccountsResponse]
     *
     */
    fun linkAdditionalAccounts(
        owner: KeyPair,
        swapAuthority: KeyPair
    ): Flow<AccountService.LinkAdditionalAccountsResponse> {
        val request = AccountService.LinkAdditionalAccountsRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setSwapAuthority(swapAuthority.asSolanaAccountId())
            .apply { addAllSignatures(listOf(sign(owner), sign(swapAuthority))) }
            .build()

        return api::linkAdditionalAccounts
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

}