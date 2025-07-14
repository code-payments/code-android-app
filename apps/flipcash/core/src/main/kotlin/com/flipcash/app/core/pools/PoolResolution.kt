package com.flipcash.app.core.pools

import kotlinx.serialization.Serializable

@Serializable
sealed interface PoolResolution {
    @Serializable
    data object NotSet: PoolResolution

    sealed interface DecisionMade
    sealed interface HasWinner: DecisionMade

    @Serializable
    data class BooleanResolution(val value: Boolean): PoolResolution, HasWinner
    @Serializable
    data object Refund: PoolResolution, DecisionMade
}