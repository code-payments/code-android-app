package com.flipcash.services.internal.model.pools

import com.flipcash.services.models.PoolMetadata
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature

internal sealed interface PoolRequest {
    data class Create(
        val userId: ID,
        val name: String,
        val buyIn: Fiat,
        val fundingDestination: PublicKey,
        val rendezvous: Ed25519.KeyPair,
    ): PoolRequest {
        val metadata by lazy {
            PoolMetadata.fromRequest(this)
        }
    }

    data class Get(val poolId: ID): PoolRequest

    data class Resolve(
        val pool: PoolMetadata,
        val resolution: Resolution,
        val poolRendezvous: Ed25519.KeyPair,
    ): PoolRequest {
        sealed interface Resolution {
            data class BooleanResolution(val value: Boolean): Resolution
        }
    }

    data class PlaceBet(
        val userId: ID,
        val poolId: ID,
        val payoutDestination: PublicKey,
        val poolRendezvous: Signature,
        val outcome: Outcome,
    ): PoolRequest {
        sealed interface Outcome {
            data class BooleanOutcome(val value: Boolean): Outcome
        }
    }
}