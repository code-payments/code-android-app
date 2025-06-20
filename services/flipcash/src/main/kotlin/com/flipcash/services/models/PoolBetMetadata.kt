package com.flipcash.services.models

import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.model.pools.PoolRequest.PlaceBet.Outcome
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
    val selectedOutcome: NetworkPoolBetOutcome,
    val payoutDestination: PublicKey,
    val timestamp: Instant,
) {
    companion object {
        internal fun fromRequest(request: PoolRequest.PlaceBet): PoolBetMetadata {
            return PoolBetMetadata(
                id = PublicKey.generate().bytes,
                userId = request.userId,
                selectedOutcome = when (request.outcome) {
                    is Outcome.BooleanOutcome -> NetworkPoolBetOutcome.BooleanOutcome(request.outcome.value)
                },
                payoutDestination = request.payoutDestination,
                timestamp = Clock.System.now(),
            )
        }
    }
}

@Immutable
data class NetworkPoolBet(
    val metadata: PoolBetMetadata,
    val rendezvous: Signature,
)

sealed interface NetworkPoolBetOutcome {
    data object NotSet: NetworkPoolBetOutcome
    @Immutable
    data class BooleanOutcome(val value: Boolean): NetworkPoolBetOutcome
}
