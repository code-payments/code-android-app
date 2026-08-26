package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.balance.v1.BalanceGrpcKt
import com.codeinc.opencode.gen.balance.v1.OcpBalanceService
import com.codeinc.opencode.gen.balance.v1.validate
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.solana.keys.PublicKey
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BalanceApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {

    private val api = BalanceGrpcKt.BalanceCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Returns balance data for any owner account.
     *
     * Unlike every other OpenCode endpoint, this RPC carries no auth/signature field —
     * it is intentionally unauthenticated so it can resolve the balance for any owner
     * account address, not just the caller's own. Do not sign this request.
     *
     * @param owner The owner account to fetch balance data for.
     * @return The [OcpBalanceService.GetBalanceResponse]
     */
    suspend fun getBalance(
        owner: PublicKey,
    ): OcpBalanceService.GetBalanceResponse {
        val request = OcpBalanceService.GetBalanceRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getBalance(request)
        }
    }
}
