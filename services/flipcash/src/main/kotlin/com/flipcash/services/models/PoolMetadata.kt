package com.flipcash.services.models

import com.flipcash.services.internal.model.pools.PoolRequest
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.NoId
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.annotation.concurrent.Immutable

/**
 * Represents a pool of bets.
 *
 * @property id The unique identifier of the pool.
 * @property creator The unique identifier of the user who owns the pool.
 * @property name The pool name, which should ask a yes/no question to bet against
 * @property buyIn The buy in amount for a bet
 * @property fundingDestination Destination where bet payments will be made to fund the pool
 * @property isOpen Is the pool currently open to take bets?
 * @property createdAt The date and time when the pool was created
 */
@Immutable
data class PoolMetadata(
    val id: ID,
    val creator: ID,
    val name: String,
    val buyIn: Fiat,
    val fundingDestination: PublicKey,
    val isOpen: Boolean,
    val resolution: NetworkPoolResolution,
    val createdAt: Instant,
    val closedAt: Instant?,
) {

    internal fun resolve(resolution: NetworkPoolResolution): PoolMetadata {
        return copy(resolution = resolution)
    }

    internal fun close(): PoolMetadata {
        return copy(
            isOpen = false,
            closedAt = Clock.System.now()
        )
    }

    companion object {
        val Empty = PoolMetadata(
            id = NoId,
            creator = NoId,
            name = "",
            buyIn = Fiat.Zero,
            fundingDestination = PublicKey.generate(),
            createdAt = Clock.System.now(),
            resolution = NetworkPoolResolution.NotSet,
            isOpen = true,
            closedAt = null,
        )

        internal fun fromRequest(request: PoolRequest.Create): PoolMetadata {
            return PoolMetadata(
                id = request.rendezvous.publicKeyBytes.toList(),
                creator = request.userId,
                name = request.name,
                buyIn = request.buyIn,
                resolution = NetworkPoolResolution.NotSet,
                fundingDestination = request.fundingDestination,
                createdAt = Clock.System.now(),
                isOpen = true,
                closedAt = null,
            )
        }
    }
}

@Immutable
data class NetworkPool(
    val metadata: PoolMetadata,
    val rendezvousSignature: List<Byte>,
    val bets: List<NetworkPoolBet> = emptyList(),
    val pagingToken: PagingToken,
    val isFundingDestinationInitialized: Boolean,
)

sealed interface NetworkPoolResolution {
    data object NotSet : NetworkPoolResolution

    @Immutable
    data object Refund : NetworkPoolResolution

    @Immutable
    data class BooleanResolution(val value: Boolean) : NetworkPoolResolution
}
