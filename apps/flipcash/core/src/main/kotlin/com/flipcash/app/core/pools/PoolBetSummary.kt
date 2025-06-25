package com.flipcash.app.core.pools

sealed interface PoolBetSummary {
    data object NotSet : PoolBetSummary
    data class Boolean(val numYes: Int, val numNo: Int) : PoolBetSummary
}
