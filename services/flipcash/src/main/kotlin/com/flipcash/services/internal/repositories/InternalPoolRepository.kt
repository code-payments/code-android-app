package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.PoolMapper
import com.flipcash.services.internal.model.pools.Outcome.BooleanOutcome
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.model.pools.Resolution
import com.flipcash.services.internal.network.services.PoolService
import com.flipcash.services.models.Pool
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.repository.PoolRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
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
    ): Result<PoolMetadata> {
        val request = PoolRequest.Create(
            userId = userId,
            name = name,
            buyIn = buyIn,
            fundingDestination = fundingDestination,
        )

        val pool = PoolMetadata.fromRequest(request)
        return service.createPool(owner = owner, request = request)
            .onFailure { ErrorUtils.handleError(it) }
            .map { pool }
    }

    override suspend fun getPool(poolId: ID): Result<Pool> {
        return service.getPool(request = PoolRequest.Get(poolId))
            .onFailure { ErrorUtils.handleError(it) }
            .map { poolMapper.map(it) }
    }

    override suspend fun declareOutcome(
        owner: KeyPair,
        poolId: ID,
        resolution: Boolean,
        poolRendezvous: Signature,
    ): Result<Unit> = service.resolvePool(
        owner = owner,
        request = PoolRequest.Resolve(
            poolId = poolId,
            resolution = Resolution.BooleanResolution(resolution),
            poolRendezvous = poolRendezvous,
        )
    )

    override suspend fun placeBet(
        owner: KeyPair,
        userId: ID,
        poolId: ID,
        payoutDestination: PublicKey,
        rendezvous: Signature,
        choice: Boolean,
    ): Result<Unit> = service.placeBet(
        owner = owner,
        request = PoolRequest.PlaceBet(
            poolId = poolId,
            userId = userId,
            payoutDestination = payoutDestination,
            poolRendezvous = rendezvous,
            outcome = BooleanOutcome(choice),
        )
    )
}