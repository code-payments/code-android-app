package com.flipcash.services.internal.model.pools

import com.flipcash.services.models.NetworkPoolBetOutcome
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.PoolBetMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey

internal sealed interface PoolRequest {
    data class Create(
        val userId: ID,
        val name: String,
        val buyIn: Fiat,
        val fundingDestination: PublicKey,
        val rendezvous: KeyPair,
    ): PoolRequest {
        val metadata by lazy {
            PoolMetadata.fromRequest(this)
        }
    }

    data class Get(val poolId: ID): PoolRequest

    data class GetPage(
        val queryOptions: QueryOptions,
    ): PoolRequest

    data class Close(
        val pool: PoolMetadata,
        val poolRendezvous: KeyPair,
    ): PoolRequest

    data class Resolve(
        val pool: PoolMetadata,
        val resolution: NetworkPoolResolution,
        val poolRendezvous: KeyPair,
    ): PoolRequest

    data class PlaceBet(
        val poolId: ID,
        val metadata: PoolBetMetadata,
        val poolRendezvous: KeyPair,
    ): PoolRequest
}