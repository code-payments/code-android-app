package com.flipcash.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint

/**
 * Restates this amount in the viewer's own currency ([preferredRate]'s), the only currency the app
 * shows — a 7,500 ARS tip reads as its $5 to a viewer in dollars.
 *
 * A USDF payment carries its own USD value ([LocalFiat.underlyingTokenAmount], fixed at the moment
 * it settled), so it converts from that: $5 of USDF stays $5 however far the peso has moved since.
 * Any other mint has no such anchor — `underlyingTokenAmount` holds that mint's quarks, not
 * dollars — so it crosses through today's [rates] instead, and comes back as it moved when the
 * source currency has no rate to cross with.
 *
 * Pinned against iOS by `viewer_amount.json`.
 */
fun LocalFiat.forViewer(preferredRate: Rate, rates: Map<CurrencyCode, Rate>): Fiat {
    if (mint != Mint.usdf) return nativeAmount.forViewer(preferredRate, rates)

    if (nativeAmount.currencyCode == preferredRate.currency) return nativeAmount
    val display = preferredRate.displayable(rates) ?: return nativeAmount
    return underlyingTokenAmount.convertingTo(display)
}

/**
 * Restates a plain amount in the viewer's own currency, crossing through USD. A USD-priced amount
 * (a boost chip, a group minimum) needs no source rate; any other needs one from [rates], and comes
 * back as it is when there isn't one.
 */
fun Fiat.forViewer(preferredRate: Rate, rates: Map<CurrencyCode, Rate>): Fiat {
    if (currencyCode == preferredRate.currency) return this
    val display = preferredRate.displayable(rates) ?: return this

    val usd = if (currencyCode == CurrencyCode.USD) {
        this
    } else {
        val rate = rates[currencyCode]?.takeIf { it.fx > 0.0 } ?: return this
        Fiat(fiat = decimalValue / rate.fx, currencyCode = CurrencyCode.USD)
    }
    return usd.convertingTo(display)
}

/**
 * The real rate for this rate's currency, or null when there isn't one. Before rates arrive the
 * exchange stands the preferred currency in at 1:1 with the dollar, which would pass every amount
 * through as if the viewer read dollars, so a non-USD rate is taken from [rates] rather than trusted.
 */
private fun Rate.displayable(rates: Map<CurrencyCode, Rate>): Rate? {
    if (currency == CurrencyCode.USD) return takeIf { fx > 0.0 }
    return rates[currency]?.takeIf { it.fx > 0.0 }
}

/** [LocalFiat.forViewer] against the exchange's current preferred currency, following it as it changes. */
@Composable
fun LocalFiat.inDisplayCurrency(): Fiat {
    val (preferredRate, rates) = displayRates()
    return forViewer(preferredRate, rates)
}

/** [Fiat.forViewer] against the exchange's current preferred currency, following it as it changes. */
@Composable
fun Fiat.inDisplayCurrency(): Fiat {
    val (preferredRate, rates) = displayRates()
    return forViewer(preferredRate, rates)
}

// Observed rather than read once: the viewer can change their currency from a screen stacked over
// the one showing the amount, and nothing else would bring the new currency back down.
@Composable
private fun displayRates(): Pair<Rate, Map<CurrencyCode, Rate>> {
    val exchange = LocalExchange.current
    val preferredRate by exchange.observePreferredRate().collectAsState(initial = exchange.preferredRate)
    val rates by exchange.observeRates().collectAsState(initial = exchange.rates())
    return preferredRate to rates
}
