package com.flipcash.services.internal.model.pools

import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
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
    ): PoolRequest

    data class Get(val poolId: ID): PoolRequest

    data class DeclareOutcome(
        val poolId: ID,
        val resolution: Resolution,
        val poolRendezvous: Signature,
    ): PoolRequest

    data class PlaceBet(
        val userId: ID,
        val poolId: ID,
        val payoutDestination: PublicKey,
        val poolRendezvous: Signature,
        val choiceType: BetChoiceType,
    ): PoolRequest
}

sealed interface BetChoiceType {
    data class BooleanChoice(val value: Boolean): BetChoiceType
}

sealed interface Resolution {
    data class BooleanResolution(val value: Boolean): Resolution

}