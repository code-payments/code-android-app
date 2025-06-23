package com.flipcash.services.repository

import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

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
        poolId: ID
    ): Result<NetworkPool>

    suspend fun getPagedPools(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<List<NetworkPool>>

    suspend fun declareOutcome(
        owner: KeyPair,
        pool: PoolMetadata,
        resolution: Boolean,
        rendezvous: KeyPair,
    ): Result<Unit>

    suspend fun placeBet(
        owner: KeyPair,
        userId: ID,
        poolId: ID,
        payoutDestination: PublicKey,
        rendezvous: KeyPair,
        choice: Boolean,
    ): Result<Unit>
}