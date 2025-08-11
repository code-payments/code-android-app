package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.PoolGrpcKt
import com.codeinc.flipcash.gen.pool.v1.PoolService
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.network.extensions.asPoolId
import com.flipcash.services.internal.network.extensions.asQueryOptions
import com.flipcash.services.internal.network.extensions.asSignature
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.internal.network.extensions.toProto
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolMetadata
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
        val metadata = request.metadata
        val pool = metadata.toProto()
        val signature = request.rendezvous.sign(pool.toByteArray())
        val rpcRequest = PoolService.CreatePoolRequest.newBuilder()
            .setPool(pool)
            .setRendezvousSignature(signature.asSignature())
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.createPool(rpcRequest)
        }
    }

    /**
     * Gets pool metadata by its ID
     */
    suspend fun getPool(
        owner: KeyPair,
        request: PoolRequest.Get,
    ): PoolService.GetPoolResponse {
        val rpcRequest = PoolService.GetPoolRequest.newBuilder()
            .setId(request.poolId.asPoolId())
            .setExcludeBets(request.excludeBets)
            .setIncludeUserProfiles(request.includeUserProfiles)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getPool(rpcRequest)
        }
    }

    /**
     * Gets all pools for a user over a paging API
     *
     * Note: Only bet summaries are provided in the response
     */
    suspend fun getPagedPools(
        owner: KeyPair,
        request: PoolRequest.GetPage,
    ): PoolService.GetPagedPoolsResponse {
        val rpcRequest = PoolService.GetPagedPoolsRequest.newBuilder()
            .setQueryOptions(request.queryOptions.asQueryOptions())
            .apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.getPagedPools(rpcRequest)
        }
    }

    /**
     * Closes a pool from additional bets
     */
    suspend fun closePool(
        owner: KeyPair,
        request: PoolRequest.Close,
    ): PoolService.ClosePoolResponse {
        val pool = request.pool
        val metadata = pool.toProto()
        val signature = request.poolRendezvous.sign(metadata.toByteArray())

        val rpcRequest = PoolService.ClosePoolRequest.newBuilder()
            .setId(metadata.id)
            .setClosedAt(metadata.closedAt)
            .setNewRendezvousSignature(signature.asSignature())
            .apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.closePool(rpcRequest)
        }
    }

    /**
     * Resolves a pool by declaring the pool's outcome. The pool creator
     * resolves a pool by calling this RPC first, then SubmitIntent to distribute funds
     * to the winning participants.
     */
    suspend fun resolvePool(
        owner: KeyPair,
        request: PoolRequest.Resolve,
    ): PoolService.ResolvePoolResponse {
        val pool = request.pool
        val metadata = pool.toProto()
        val signature = request.poolRendezvous.sign(metadata.toByteArray()).asSignature()

        val rpcRequest = PoolService.ResolvePoolRequest.newBuilder()
            .setId(pool.id.asPoolId())
            .setResolution(
                when (pool.resolution) {
                    is NetworkPoolResolution.BooleanResolution -> {
                        Model.Resolution.newBuilder()
                            .setBooleanResolution(pool.resolution.value)
                    }
                    is NetworkPoolResolution.Refund -> {
                        Model.Resolution.newBuilder()
                            .setRefundResolution(Model.Resolution.Refund.getDefaultInstance())
                    }
                    is NetworkPoolResolution.NotSet -> throw IllegalStateException("Not set resolution")
                }
            )
            .setNewRendezvousSignature(signature)
            .apply { setAuth(authenticate(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.resolvePool(rpcRequest)
        }
    }

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
        val bet = request.metadata
        val metadata = bet.toProto()
        val signature = request.poolRendezvous.sign(metadata.toByteArray())
        val rpcRequest = PoolService.MakeBetRequest.newBuilder()
            .setPoolId(request.poolId.asPoolId())
            .setBet(metadata)
            .setRendezvousSignature(signature.asSignature())
            .apply {
                setAuth(authenticate(owner))
            }.build()

        return withContext(Dispatchers.IO) {
            api.makeBet(rpcRequest)
        }
    }
}