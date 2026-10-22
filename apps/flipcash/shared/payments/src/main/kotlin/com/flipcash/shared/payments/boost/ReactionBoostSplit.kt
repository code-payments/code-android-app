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

/** The kind of chat a boost is made in, which decides who can be paid. */
sealed interface BoostChat {
    /** A group, whose [creator] takes a share of boosts on other members' messages. */
    data class Group(val creator: ID) : BoostChat

    /** A direct chat: there is no creator, and the other member is paid the whole. */
    data object Dm : BoostChat
}

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
 * When a member boosts another member's message, the creator gets [CREATOR_PERCENT] of the total,
 * rounded half-even to the cent, and the sender gets the rest. When the creator pays, or the
 * creator wrote the message, the one remaining payee gets the whole. In a DM ([BoostChat.Dm]) the
 * sender gets the whole as one payment. Each share must be at least [MINIMUM_SHARE_CENTS], which
 * puts the smallest two-payee boost at three cents.
 */
object ReactionBoostSplit {
    const val CREATOR_PERCENT = 20L
    const val MINIMUM_SHARE_CENTS = 1L

    fun plan(payer: ID, sender: ID, chat: BoostChat, totalCents: Long): ReactionBoostPlan {
        if (payer == sender) return ReactionBoostPlan.SelfBoost
        val shares = when (chat) {
            BoostChat.Dm -> listOf(BoostShare(sender, BoostPayee.SENDER, totalCents))
            is BoostChat.Group -> groupShares(payer, sender, chat.creator, totalCents)
        }
        if (shares.any { it.cents < MINIMUM_SHARE_CENTS }) return ReactionBoostPlan.ShareBelowMinimum
        return ReactionBoostPlan.Pay(shares)
    }

    private fun groupShares(payer: ID, sender: ID, creator: ID, totalCents: Long): List<BoostShare> = when {
        payer == creator -> listOf(BoostShare(sender, BoostPayee.SENDER, totalCents))
        sender == creator -> listOf(BoostShare(creator, BoostPayee.CREATOR, totalCents))
        else -> {
            val creatorCents = creatorShareCents(totalCents)
            listOf(
                BoostShare(sender, BoostPayee.SENDER, totalCents - creatorCents),
                BoostShare(creator, BoostPayee.CREATOR, creatorCents),
            )
        }
    }

    /** round_half_even(total × [CREATOR_PERCENT] / 100), to the cent. */
    internal fun creatorShareCents(totalCents: Long): Long =
        BigDecimal.valueOf(totalCents * CREATOR_PERCENT, 2)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
}
