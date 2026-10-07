package com.flipcash.shared.chat

import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate

/**
 * The stored minimum balance for a keypad [entered] amount in [rate]'s currency, or null when it
 * falls below the smallest USD unit or there is no usable rate to restate it with.
 *
 * The keypad enters in the preferred currency; the rule is compared against a USD balance, so it
 * is stored in USD rather than in whatever was typed, rounded to USD's decimals (cents) so the
 * server never sees a fraction of one. Shared by group creation and the Edit Group requirement
 * editor so both write the same amount for the same entry.
 */
fun minimumBalanceFor(entered: Double, rate: Rate): Fiat? {
    if (entered <= 0.0) return null
    // `Rate.ignore` carries a near-zero fx, which would restate any entry as an enormous USD sum.
    if (!rate.isUsable() || rate.fx <= 0.0) return null
    val usd = Fiat(entered, rate.currency).convertingToUsdIfNeeded(rate)
    val rounded = usd.rounded(CurrencyCode.USD.fractionDigits)
    return rounded.takeIf { it.decimalValue > 0.0 }
}
