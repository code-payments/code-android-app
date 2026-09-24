package com.flipcash.app.tipping.internal

import com.getcode.solana.keys.Mint

/**
 * What a group's balance requirement is measured in — the currency sheet's header card (node
 * 10370:997) or one of the tokens under it (node 10372:1015).
 *
 * [All] is not the absence of a choice. `MinimumBalanceRequirement.mints` left empty means "any
 * currency", and the server measures it against every holding added up in USD, USDF included; so it
 * is a rule in its own right and the one the form opens on.
 */
internal sealed interface GroupCurrency {

    /** The mints `MinimumBalanceRequirement` carries: none for [All], the one for [Specific]. */
    val mints: List<Mint>

    data object All : GroupCurrency {
        override val mints: List<Mint> = emptyList()
    }

    data class Specific(val mint: Mint) : GroupCurrency {
        override val mints: List<Mint> = listOf(mint)
    }
}
