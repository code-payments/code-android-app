package com.flipcash.app.core.pools

sealed interface PoolBetOutcome {
    data object NotSet: PoolBetOutcome
    sealed interface DecisionMade
    data class BooleanOutcome(val value: Boolean): PoolBetOutcome, DecisionMade
}