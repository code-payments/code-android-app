package com.flipcash.services.repository

import com.flipcash.services.models.Pool
import com.flipcash.services.models.PoolMetadata
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
    ): Result<PoolMetadata>

    suspend fun getPool(
        poolId: ID
    ): Result<Pool>

    suspend fun declareOutcome(
        owner: KeyPair,
        poolId: ID,
        resolution: Boolean,
        poolRendezvous: Signature,
    ): Result<Unit>

    suspend fun placeBet(
        owner: KeyPair,
        userId: ID,
        poolId: ID,
        payoutDestination: PublicKey,
        rendezvous: Signature,
        choice: Boolean,
    ): Result<Unit>
}