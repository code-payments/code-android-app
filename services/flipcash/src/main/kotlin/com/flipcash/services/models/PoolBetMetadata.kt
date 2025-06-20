package com.flipcash.services.models

import com.flipcash.services.internal.model.pools.Outcome
import com.flipcash.services.internal.model.pools.PoolRequest
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.annotation.concurrent.Immutable

@Immutable
data class PoolBetMetadata(
    val id: ID,
    val userId: ID,
    val selectedOutcome: BetOutcome,
    val payoutDestination: PublicKey,
    val timestamp: Instant,
) {
    companion object {
        internal fun fromRequest(request: PoolRequest.PlaceBet): PoolBetMetadata {
            return PoolBetMetadata(
                id = PublicKey.generate().bytes,
                userId = request.userId,
                selectedOutcome = when (request.outcome) {
                    is Outcome.BooleanOutcome -> BetOutcome.BooleanOutcome(request.outcome.value)
                },
                payoutDestination = request.payoutDestination,
                timestamp = Clock.System.now(),
            )
        }
    }
}

@Immutable
data class PoolBet(
    val metadata: PoolBetMetadata,
    val rendezvous: Signature,
)

sealed interface BetOutcome {
    data object NotSet: BetOutcome
    @Immutable
    data class BooleanOutcome(val value: Boolean): BetOutcome
}
