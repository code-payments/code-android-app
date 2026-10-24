package com.flipcash.app.core.ui

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint

/**
 * What an activity row shows for its amount.
 *
 * @param viewer the entry in the currency the viewer reads money in — always the row's headline.
 * @param transferred what actually moved, set only when the payment was denominated in a currency
 * that isn't the viewer's; null when the two are the same and a second line would just repeat the
 * first.
 */
data class ViewerAmount(
    val viewer: Fiat,
    val transferred: Fiat?,
)

/**
 * Restates this amount in the viewer's own currency ([preferredRate]'s), keeping what was actually
 * transferred alongside it when the two differ — a 7,500 ARS tip reads as its $5 to a viewer in
 * dollars, with the pesos underneath.
 *
 * A USDF payment carries its own USD value ([LocalFiat.underlyingTokenAmount], fixed at the moment
 * it settled), so it converts from that: $5 of USDF stays $5 however far the peso has moved since.
 * Any other mint has no such anchor — `underlyingTokenAmount` holds that mint's quarks, not
 * dollars — so it crosses through today's [rates] instead, and falls back to the transferred amount
 * alone when the source currency has no rate to cross with.
 */
fun LocalFiat.forViewer(preferredRate: Rate, rates: Map<CurrencyCode, Rate>): ViewerAmount {
    val transferred = nativeAmount
    if (transferred.currencyCode == preferredRate.currency) return ViewerAmount(transferred, null)

    val usd = usdValue(rates) ?: return ViewerAmount(transferred, null)
    return ViewerAmount(viewer = usd.convertingTo(preferredRate), transferred = transferred)
}

/** The entry's value in USD, or null when it can't be established. See [forViewer]. */
private fun LocalFiat.usdValue(rates: Map<CurrencyCode, Rate>): Fiat? {
    if (mint == Mint.usdf) return underlyingTokenAmount

    val rate = rates[nativeAmount.currencyCode]?.takeIf { it.fx > 0.0 } ?: return null
    return Fiat(fiat = nativeAmount.decimalValue / rate.fx, currencyCode = CurrencyCode.USD)
}
