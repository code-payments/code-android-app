package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.PoolMapper
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.network.services.PoolService
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.PoolRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils

internal class InternalPoolRepository(
    private val service: PoolService,
    private val poolMapper: PoolMapper,
) : PoolRepository {

    override suspend fun createPool(
        owner: KeyPair,
        userId: ID,
        name: String,
        buyIn: Fiat,
        fundingDestination: PublicKey,
        rendezvous: KeyPair,
    ): Result<PoolMetadata> {
        val request = PoolRequest.Create(
            userId = userId,
            name = name,
            buyIn = buyIn,
            fundingDestination = fundingDestination,
            rendezvous = rendezvous,
        )

        return service.createPool(owner = owner, request = request)
            .onFailure { ErrorUtils.handleError(it) }
            .map { request.metadata }
    }

    override suspend fun getPool(poolId: ID, excludeBets: Boolean): Result<NetworkPool> {
        return service.getPool(request = PoolRequest.Get(poolId, excludeBets))
            .onFailure { ErrorUtils.handleError(it) }
            .map { poolMapper.map(it) }
            .fold(
                onSuccess = { pool ->
//                    val isValid = Ed25519.verify(
//                        pool.rendezvousSignature.toByteArray(),
//                        pool.metadata.toProto().toByteArray(),
//                        pool.metadata.id.toByteArray()
//                    )
//
//                    if (!isValid) return@fold Result.failure(Exception("Invalid pool"))
                    Result.success(pool)
                },
                onFailure = { return Result.failure(it) }
            )
    }

    override suspend fun getPagedPools(
        owner: KeyPair,
        queryOptions: QueryOptions
    ): Result<List<NetworkPool>> {
        return service.getPagedPools(owner = owner, request = PoolRequest.GetPage(queryOptions))
            .map { list -> list.map { poolMapper.map(it) } }
            .map { pools ->
                pools.filter { pool ->
                    true
//                    val isValid = Ed25519.verify(
//                        pool.rendezvousSignature.toByteArray(),
//                        pool.metadata.toProto().toByteArray(),
//                        pool.metadata.id.toByteArray()
//                    )
//                    println("isValid: $isValid")
//                    isValid
                }
            }

    }

    override suspend fun closePool(
        owner: KeyPair,
        pool: PoolMetadata,
        poolRendezvous: KeyPair,
    ): Result<Unit> {
        return service.closePool(
            owner = owner,
            request = PoolRequest.Close(
                pool = pool,
                poolRendezvous = poolRendezvous
            )
        )
    }

    override suspend fun declareOutcome(
        owner: KeyPair,
        pool: PoolMetadata,
        poolRendezvous: KeyPair,
        resolution: NetworkPoolResolution
    ): Result<Unit> = service.resolvePool(
        owner = owner,
        request = PoolRequest.Resolve(
            pool = pool,
            resolution = resolution,
            poolRendezvous = poolRendezvous,
        )
    )

    override suspend fun placeBet(
        owner: KeyPair,
        poolId: ID,
        poolRendezvous: KeyPair,
        metadata: PoolBetMetadata,
    ): Result<PoolBetMetadata> {
        val request = PoolRequest.PlaceBet(
            poolId = poolId,
            poolRendezvous = poolRendezvous,
            metadata = metadata,
        )

        return service.placeBet(
            owner = owner,
            request = request,
        ).fold(
            onSuccess = {
                Result.success(request.metadata)
            },
            onFailure = { Result.failure(it) }
        )
    }
}