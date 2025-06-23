package com.flipcash.app.core.pools

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R

sealed interface PoolBetOutcome {
    val key: String

    data object NotSet : PoolBetOutcome {
        override val key: String = "bet::not_set"
    }

    sealed interface DecisionMade
    data class BooleanOutcome(val value: Boolean) : PoolBetOutcome, DecisionMade {
        override val key: String = "bet::boolean::$value"
    }
}

val PoolBetOutcome.label: String
    @Composable get() = when (this) {
        PoolBetOutcome.NotSet -> stringResource(R.string.action_pool_bet_not_set)
        is PoolBetOutcome.BooleanOutcome -> if (value) {
            stringResource(R.string.action_pool_bet_yes)
        } else {
            stringResource(R.string.action_pool_bet_no)
        }
    }

val PoolResolution.winningOutcome: PoolBetOutcome?
    get() = when (val res = this) {
        PoolResolution.NotSet -> null
        is PoolResolution.BooleanResolution -> PoolBetOutcome.BooleanOutcome(res.value)
        PoolResolution.Refund -> null
    }