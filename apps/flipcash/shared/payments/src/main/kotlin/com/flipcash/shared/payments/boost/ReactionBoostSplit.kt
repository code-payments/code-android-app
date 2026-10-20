package com.flipcash.shared.payments.boost

import com.getcode.opencode.model.core.ID
import java.math.BigDecimal
import java.math.RoundingMode

/** Who a share of a reaction boost goes to. */
enum class BoostPayee {
    /** The person who wrote the boosted message. */
    SENDER,

    /** The group's creator. */
    CREATOR,
}

/** One payee's part of a boost, in whole USD cents. */
data class BoostShare(val payee: ID, val role: BoostPayee, val cents: Long)

/** What a boost should pay, from [ReactionBoostSplit.plan]. */
sealed interface ReactionBoostPlan {
    /**
     * Pay [shares]: one share is a single payment of the whole, two are one multi-payment listed
     * sender then creator.
     */
    data class Pay(val shares: List<BoostShare>) : ReactionBoostPlan {
        val totalCents: Long get() = shares.sumOf { it.cents }
    }

    /** The payer wrote the message. */
    data object SelfBoost : ReactionBoostPlan

    /** A share would be under [ReactionBoostSplit.MINIMUM_SHARE_CENTS]. */
    data object ShareBelowMinimum : ReactionBoostPlan
}

/**
 * Picks the payees and per-share amounts for a reaction boost. A cross-platform parity hotspot: it
 * must agree with iOS at every vector in `test-vectors/reaction-boost-split.json`.
 *
 * When anyone boosts another member's message, the creator gets [CREATOR_PERCENT] of the total,
 * rounded half-even to the cent, and the sender gets the rest. That holds when the creator pays
 * too: their share loops back to them. When the creator wrote the message, they get the whole.
 * Each share must be at least
 * [MINIMUM_SHARE_CENTS], which puts the smallest two-payee boost at three cents.
 */
object ReactionBoostSplit {
    const val CREATOR_PERCENT = 20L
    const val MINIMUM_SHARE_CENTS = 1L

    fun plan(payer: ID, sender: ID, creator: ID, totalCents: Long): ReactionBoostPlan {
        if (payer == sender) return ReactionBoostPlan.SelfBoost
        val shares = when {
            sender == creator -> listOf(BoostShare(creator, BoostPayee.CREATOR, totalCents))
            else -> {
                val creatorCents = creatorShareCents(totalCents)
                listOf(
                    BoostShare(sender, BoostPayee.SENDER, totalCents - creatorCents),
                    BoostShare(creator, BoostPayee.CREATOR, creatorCents),
                )
            }
        }
        if (shares.any { it.cents < MINIMUM_SHARE_CENTS }) return ReactionBoostPlan.ShareBelowMinimum
        return ReactionBoostPlan.Pay(shares)
    }

    /** round_half_even(total × [CREATOR_PERCENT] / 100), to the cent. */
    internal fun creatorShareCents(totalCents: Long): Long =
        BigDecimal.valueOf(totalCents * CREATOR_PERCENT, 2)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
}
