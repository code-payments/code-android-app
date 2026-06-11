package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint

/**
 * Pure logic for resolving which token should be selected based on balances and exchange rate.
 *
 * Returns the [Mint] that should be selected, or `null` if no valid token exists.
 */
internal fun resolveTokenSelection(
    balances: Map<Mint, Fiat>,
    currentSelection: Mint?,
    rate: Rate,
): Mint? {
    if (balances.isEmpty()) return null

    val baseline = 0.01.toFiat(rate.currency)

    fun Fiat.meetsThreshold(): Boolean {
        val native = convertingTo(rate)
        return native.valueNonZero() && native.valueGreaterThanOrEqualTo(baseline)
    }

    // Keep current selection if it still meets the threshold
    if (currentSelection != null) {
        val balance = balances[currentSelection]
        if (balance != null && balance.meetsThreshold()) {
            return currentSelection
        }
    }

    // Fall back to the highest balance that meets the threshold
    return balances
        .filter { it.value.meetsThreshold() }
        .maxByOrNull { it.value }
        ?.key
}
