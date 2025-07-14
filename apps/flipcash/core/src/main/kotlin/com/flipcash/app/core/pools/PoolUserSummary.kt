package com.flipcash.app.core.pools

import com.getcode.opencode.model.financial.Fiat

sealed interface PoolUserSummary {
    data object NotSet : PoolUserSummary
    data class Won(val amountWon: Fiat, val amountReceived: Fiat): PoolUserSummary
    data class Lost(val amount: Fiat): PoolUserSummary
    data class Refunded(val amount: Fiat): PoolUserSummary
}