package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.PoolGrpcKt
import com.codeinc.flipcash.gen.pool.v1.PoolService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.model.pools.Resolution
import com.flipcash.services.internal.network.extensions.asPoolId
import com.flipcash.services.internal.network.extensions.asSignature
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.sign
import com.flipcash.services.internal.network.extensions.signedMetadata
import com.flipcash.services.models.Pool
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.GrpcApi
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class PoolApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = PoolGrpcKt.PoolCoroutineStub(managedChannel)
        .withWaitForReady()

    /**
     * Creates a new pool
     */
    suspend fun createPool(
        owner: KeyPair,
        request: PoolRequest.Create,
    ): PoolService.CreatePoolResponse {
        val pool = PoolMetadata.fromRequest(request)
        val rpcRequest = PoolService.CreatePoolRequest.newBuilder()
            .setPool(pool.signedMetadata())
            .apply {
                setRendezvousSignature(sign(owner))
            }
            .apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.createPool(rpcRequest)
        }
    }

    /**
     * Gets pool metadata by its ID
     */
    suspend fun getPool(
        request: PoolRequest.Get,
    ): PoolService.GetPoolResponse {
        val rpcRequest = PoolService.GetPoolRequest.newBuilder()
            .setId(request.poolId.asPoolId())
            .build()

        return withContext(Dispatchers.IO) {
            api.getPool(rpcRequest)
        }
    }

    suspend fun declareOutcome(
        owner: KeyPair,
        request: PoolRequest.DeclareOutcome,
    ): PoolService.DeclarePoolOutcomeResponse {
        val rpcRequest = PoolService.DeclarePoolOutcomeRequest.newBuilder()
            .setId(request.poolId.asPoolId())
            .setResolution(
                when (request.resolution) {
                    is Resolution.BooleanResolution -> {
                        Model.Resolution.newBuilder()
                            .setBooleanResolution(request.resolution.value)
                    }
                }
            )
            .apply {
                setNewRendezvousSignature(sign(owner))
            }
            .apply {
                setAuth(authenticate(owner))
            }
            .build()

        return withContext(Dispatchers.IO) {
            api.declarePoolOutcome(rpcRequest)
        }
    }

    // MakeBet creates a new bet against a pool. Pool participants make a bet by
    // calling MakeBet to create an initially unpaid bet, then SubmitIntent for
    // payment where:
    //  1. Intent ID == Bet.id
    //  2. Payment amount == PoolMetadata.buy_in
    //  3. Payment destination == PoolMetadata.funding_destination
    // Bets without payment, or with invalid intents, will not be visible in the
    // PoolMetadata when calling GetPool.
    /**
     * Creates a new bet against a pool. Pool participants make a bet by
     * calling [placeBet] to create an initially unpaid bet, then SubmitIntent for
     * payment where:
     *  1. Intent ID == Bet.id
     *  2. Payment amount == PoolMetadata.buy_in
     *  3. Payment destination == PoolMetadata.funding_destination
     *
     *  Bets without payment, or with invalid intents, will not be visible in the
     *  [PoolMetadata] when calling [getPool].
     */
    suspend fun placeBet(
        owner: KeyPair,
        request: PoolRequest.PlaceBet,
    ): PoolService.MakeBetResponse {
        val bet = PoolBetMetadata.fromRequest(request)
        val rpcRequest = PoolService.MakeBetRequest.newBuilder()
            .setPoolId(request.poolId.asPoolId())
            .setBet(bet.signedMetadata())
            .setRendezvousSignature(request.poolRendezvous.byteArray.asSignature())
            .apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.makeBet(rpcRequest)
        }
    }
}