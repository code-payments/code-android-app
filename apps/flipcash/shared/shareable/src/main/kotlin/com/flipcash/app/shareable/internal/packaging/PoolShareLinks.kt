package com.flipcash.app.shareable.internal.packaging

import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolBetSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
internal data class PoolShareLinkData(
    @SerialName("p")
    val title: String,
    @SerialName("a")
    val buyIn: String,
    @SerialName("y")
    val yesVotes: Int,
    @SerialName("n")
    val noVotes: Int,
) {
    constructor(pool: Pool): this(
        title = pool.name,
        buyIn = pool.buyIn.formatted(),
        yesVotes = when (val summary = pool.betSummary) {
            is PoolBetSummary.Boolean -> summary.numYes
            PoolBetSummary.NotSet -> 0
        },
        noVotes = when (val summary = pool.betSummary) {
            is PoolBetSummary.Boolean -> summary.numNo
            PoolBetSummary.NotSet -> 0
        },
    )
}