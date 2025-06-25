package com.flipcash.services.repository

import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey

interface PoolRepository {
    suspend fun createPool(
        owner: KeyPair,
        userId: ID,
        name: String,
        buyIn: Fiat,
        fundingDestination: PublicKey,
        rendezvous: KeyPair,
    ): Result<PoolMetadata>

    suspend fun getPool(
        poolId: ID,
    ): Result<NetworkPool>

    suspend fun getPagedPools(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<List<NetworkPool>>

    suspend fun closePool(
        owner: KeyPair,
        pool: PoolMetadata,
        poolRendezvous: KeyPair,
    ): Result<Unit>

    suspend fun declareOutcome(
        owner: KeyPair,
        pool: PoolMetadata,
        poolRendezvous: KeyPair,
        resolution: NetworkPoolResolution,
    ): Result<Unit>

    suspend fun placeBet(
        owner: KeyPair,
        poolId: ID,
        poolRendezvous: KeyPair,
        metadata: PoolBetMetadata,
    ): Result<PoolBetMetadata>
}