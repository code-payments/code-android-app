package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.grossingUpLaunchpadSellFee
import com.getcode.opencode.model.financial.launchpadSellFee
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.spendableUnderGrossedUpSellFee
import com.getcode.opencode.model.financial.spendableUnderSellFeeOnTop

/**
 * Pure logic for trimming an entered amount down to what its funding [balance] can actually cover
 * once the fee is applied.
 *
 * Amount entry is capped at the raw balance, so the only thing that can push the debit past it is
 * the fee: charged on top, the debit is `entered × (1 + f)`; grossed up out of a launchpad sale, it
 * is `entered / (1 - f)`. Entering the maximum therefore always overruns — by the fee, and never by
 * more — which is why the correction is applied silently instead of being put to the user.
 *
 * Returns the corrected entry, floored to the currency's smallest unit so re-deriving the debit
 * from it never lands back over the balance, or `null` when the entry already fits and should stay
 * exactly as typed. [entered] is interpreted in [balance]'s currency.
 */
internal fun entryAffordableAfterFee(
    entered: Double,
    balance: Fiat,
    feeBps: Int,
    feeChargedOnTop: Boolean,
): Fiat? {
    if (feeBps <= 0) return null

    val enteredFiat = Fiat(entered, balance.currencyCode)
    val debit = if (feeChargedOnTop) {
        enteredFiat + enteredFiat.launchpadSellFee(feeBps)
    } else {
        enteredFiat.grossingUpLaunchpadSellFee(feeBps)
    }
    if (debit <= balance) return null

    return if (feeChargedOnTop) {
        balance.spendableUnderSellFeeOnTop(feeBps)
    } else {
        balance.spendableUnderGrossedUpSellFee(feeBps)
    }.flooredToSmallestUnit()
}
