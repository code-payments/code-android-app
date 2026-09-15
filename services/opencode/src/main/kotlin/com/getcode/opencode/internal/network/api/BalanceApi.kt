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
     * Returns balance data for a set of owner accounts, optionally filtered to a
     * set of mints.
     *
     * Unlike every other OpenCode endpoint, this RPC carries no auth/signature field —
     * it is intentionally unauthenticated so it can resolve balances for any owner
     * account address, not just the caller's own. Do not sign this request.
     *
     * @param owners The owner accounts to fetch balance data for (min 1, max 1024).
     * @param mints Optional filter to limit the response to balances for these mints.
     *   When empty, balances for all mints held by each owner are returned.
     * @return The [OcpBalanceService.GetBalancesResponse]
     */
    suspend fun getBalances(
        owners: List<PublicKey>,
        mints: List<PublicKey> = emptyList(),
    ): OcpBalanceService.GetBalancesResponse {
        val request = OcpBalanceService.GetBalancesRequest.newBuilder()
            .addAllOwners(owners.map { it.asSolanaAccountId() })
            .addAllMints(mints.map { it.asSolanaAccountId() })
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getBalances(request)
        }
    }
}
