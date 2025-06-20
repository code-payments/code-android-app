package com.flipcash.app.core.pools

sealed interface PoolResolution {
    data object NotSet: PoolResolution

    sealed interface DecisionMade
    data class BooleanResolution(val value: Boolean): PoolResolution, DecisionMade
}