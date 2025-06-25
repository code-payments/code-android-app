package com.flipcash.app.core.pools

sealed interface PoolResolution {
    data object NotSet: PoolResolution

    sealed interface DecisionMade
    sealed interface HasWinner: DecisionMade
    data class BooleanResolution(val value: Boolean): PoolResolution, HasWinner
    data object Refund: PoolResolution, DecisionMade
}