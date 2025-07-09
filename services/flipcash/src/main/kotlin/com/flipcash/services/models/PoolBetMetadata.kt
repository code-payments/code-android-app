package com.flipcash.services.models

import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import kotlin.time.Instant
import javax.annotation.concurrent.Immutable

@Immutable
data class PoolBetMetadata(
    val id: ID,
    val userId: ID,
    val selectedOutcome: NetworkPoolBetOutcome,
    val payoutDestination: PublicKey,
    val timestamp: Instant,
)

@Immutable
data class NetworkPoolBet(
    val metadata: PoolBetMetadata,
    val poolRendezvousSignature: List<Byte>,
    val hasIntentBeenSubmitted: Boolean,
)

sealed interface NetworkPoolBetOutcome {
    data object NotSet: NetworkPoolBetOutcome
    @Immutable
    data class BooleanOutcome(val value: Boolean): NetworkPoolBetOutcome
}
