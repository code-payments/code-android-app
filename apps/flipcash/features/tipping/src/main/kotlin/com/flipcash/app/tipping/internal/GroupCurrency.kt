package com.flipcash.app.tipping.internal

import com.getcode.solana.keys.Mint

/**
 * What a group's balance requirement is measured in — the currency sheet's "Any Community
 * Currency" entry or one of the tokens above it.
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

/**
 * The rows the currency sheet offers as specific currencies: everything but USDF. Dollars aren't a
 * community currency, and "Any Community Currency" already covers them in the USD total. The sheet
 * is the only thing that sets [GroupCurrency.Specific], so a draft can't hold USDF.
 */
internal fun <T> List<T>.withoutUsdf(mintOf: (T) -> Mint): List<T> =
    filterNot { mintOf(it) == Mint.usdf }
