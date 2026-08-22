package com.getcode.opencode.model.financial

/**
 * Launchpad pool sell-fee math, the fiat-domain analog of iOS's
 * `ExchangedFiat+LaunchpadSellFee`.
 *
 * Unlike iOS — which computes the fee in token quarks and needs an
 * overflow-safe split multiply — Android never multiplies token quarks by bps
 * on the client. The pool deducts the fee on-chain during the swap; the client
 * only works in the fiat domain (a [Fiat]'s `Long` micro-unit quarks), where no
 * realistic amount overflows. Both helpers cap bps at 10_000 (100%) to match
 * the on-chain clamp.
 */

private const val MAX_FEE_BPS = 10_000

/**
 * The launchpad pool's sell fee taken from this amount when it is sold:
 * `amount × bps / 10_000`.
 */
fun Fiat.launchpadSellFee(bps: Int): Fiat {
    val cappedBps = bps.coerceIn(0, MAX_FEE_BPS)
    return this * (cappedBps / MAX_FEE_BPS.toDouble())
}

/**
 * Grosses this net amount up so that, after the launchpad pool's sell fee is
 * deducted on-chain, the proceeds net back to this amount:
 * `net / (1 − bps/10_000)`.
 *
 * A 100% (or greater) fee can't be grossed up — the guard returns the amount
 * unchanged instead of dividing by zero (or going negative). Server-sourced bps
 * drives this, and the flow gates insufficient funds downstream regardless.
 */
fun Fiat.grossingUpLaunchpadSellFee(bps: Int): Fiat {
    val cappedBps = bps.coerceIn(0, MAX_FEE_BPS)
    if (cappedBps >= MAX_FEE_BPS) return this
    val feeFraction = cappedBps / MAX_FEE_BPS.toDouble()
    return this / (1.0 - feeFraction)
}

/**
 * The most of this balance that can be spent when the pool's sell fee is *grossed up* out of the
 * spend: `balance × (1 − bps/10_000)`, the inverse of [grossingUpLaunchpadSellFee].
 *
 * Grossing the result back up lands exactly on this balance, which makes it the largest entry the
 * balance can actually fund.
 */
fun Fiat.spendableUnderGrossedUpSellFee(bps: Int): Fiat = this - launchpadSellFee(bps)

/**
 * The most of this balance that can be spent when the fee is charged *on top* of the spend rather
 * than skimmed out of it: `balance / (1 + bps/10_000)`.
 *
 * Adding that entry's own fee back lands exactly on this balance.
 */
fun Fiat.spendableUnderSellFeeOnTop(bps: Int): Fiat {
    val cappedBps = bps.coerceIn(0, MAX_FEE_BPS)
    return this / (1.0 + cappedBps / MAX_FEE_BPS.toDouble())
}

/**
 * The flat house sell fee, in basis points, charged wherever a pool doesn't declare one of its own.
 */
const val HOUSE_SELL_FEE_BPS = 100

/**
 * This pool's declared sell fee, or the house rate when there is no pool to declare one.
 *
 * Erring high is the safe direction: the fee is grossed up into the debit, so falling back to zero
 * prices a debit the funding balance can't actually cover. iOS assumes the house rate on every leg
 * for the same reason.
 */
val LaunchpadMetadata?.sellFeeBpsOrHouseRate: Int
    get() = this?.sellFeeBps ?: HOUSE_SELL_FEE_BPS
