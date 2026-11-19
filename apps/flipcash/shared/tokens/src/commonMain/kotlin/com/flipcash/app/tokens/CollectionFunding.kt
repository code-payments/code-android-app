package com.flipcash.app.tokens

/**
 * Plans how a collection payment of a fixed amount is funded when the paying currency can't cover
 * it alone: which balances convert into the paying currency, in what order, at what fee, and how
 * much has to be added with a top-up first.
 *
 * Pure and platform-free so it can move to `:kmp:shared-core`. Every money value is a count of fiat
 * micros (1e-6 of the request currency's major unit, what [com.getcode.opencode.model.financial.Fiat]
 * stores), and every step rounds half-up on integers. Nothing here goes through `Double`: the
 * `Fiat` fee helpers can land a micro off this math, and the plan has to match iOS to the micro.
 * Legs don't go through [entryAffordableAfterFee] either, which floors to the cent and would leave
 * up to a cent behind in every drained currency.
 *
 * `test-vectors/collection_funding.json` (`CollectionFundingVectorsTest`) is the answer this and the
 * iOS planner both reproduce; `docs/specs/group-collections.md`, *Funding a payment*, has the rules.
 *
 * - The paying currency is [selected], else [lastPaid], else [dollarsId], held or not.
 * - A balance worth less than half the minor unit (5,000 micros in USD) is dust: it would display
 *   as $0.00, so it is dropped before anything else, Dollars and the paying currency included. It
 *   adds nothing to the capacity and gets no leg; the dust stays in the wallet.
 * - Funding order: the paying currency, then Dollars, then the other balances smallest first, ties
 *   by id. Each source covers only what is still needed; a source the plan doesn't reach is left
 *   alone.
 * - The paying currency and Dollars spend at full balance. Dollars convert with no fee, so its
 *   [FundingBalance.sellFeeBps] is not read. Every other balance pays its sell fee out of the amount
 *   sold.
 * - A positive shortfall gives a top-up of the shortfall rounded up to the minor unit, and at least
 *   [minTopUpMicros]. The top-up is added to Dollars and the plan is made again, so the legs
 *   describe the payment after the top-up lands.
 *
 * @param amountMicros the collection's fixed amount, in the request currency.
 * @param minorDigits the request currency's minor digits (2 for USD), which set the top-up's unit.
 * @param minTopUpMicros the smallest top-up that can be ordered, in the request currency.
 * @param dollarsId the id that names Dollars in [balances], [selected] and [lastPaid].
 * @param selected the currency picked in this payment, or null.
 * @param lastPaid the currency of the last successful collection payment, or null.
 * @param balances every held balance, valued in the request currency. Ids must be distinct.
 */
fun planCollectionFunding(
    amountMicros: Long,
    minorDigits: Int,
    minTopUpMicros: Long,
    dollarsId: String,
    selected: String?,
    lastPaid: String?,
    balances: List<FundingBalance>,
): CollectionFundingPlan {
    require(amountMicros >= 0) { "amount $amountMicros is negative" }
    require(minorDigits in 0..MICRO_DIGITS) { "minor digits $minorDigits outside 0..$MICRO_DIGITS" }
    require(minTopUpMicros >= 0) { "minimum top-up $minTopUpMicros is negative" }
    require(balances.all { it.fiatMicros >= 0 }) { "a balance is negative" }
    require(balances.map { it.id }.toSet().size == balances.size) { "balance ids repeat" }

    val paying = selected ?: lastPaid ?: dollarsId
    val order = FundingOrder(paying, dollarsId)
    val unit = minorUnitMicros(minorDigits)
    val live = balances.filterNot { isDust(it.fiatMicros, unit) }

    val capacity = live.sumOf { order.spendable(it) }
    val shortfall = (amountMicros - capacity).coerceAtLeast(0)

    var topUp = 0L
    var funded = live
    if (shortfall > 0) {
        topUp = maxOf(ceilDiv(shortfall, unit) * unit, minTopUpMicros)
        funded = if (live.any { it.id == dollarsId }) {
            live.map { if (it.id == dollarsId) it.copy(fiatMicros = it.fiatMicros + topUp) else it }
        } else {
            live + FundingBalance(dollarsId, topUp, sellFeeBps = 0)
        }
    }

    val dollarsBeforeTopUp = live.firstOrNull { it.id == dollarsId }?.fiatMicros ?: 0L
    val legs = order.fund(amountMicros, funded).map { leg ->
        if (leg.source != dollarsId) return@map leg
        val fromBalance = minOf(dollarsBeforeTopUp, leg.soldMicros)
        leg.copy(dollarsSplit = DollarsSplit(fromBalance, leg.soldMicros - fromBalance))
    }

    return CollectionFundingPlan(
        payingCurrency = paying,
        shortfallMicros = shortfall,
        topUpMicros = topUp,
        legs = legs,
    )
}

/** One held balance, as the planner sees it. */
data class FundingBalance(
    val id: String,
    /** The balance's value in the request currency, at full precision. */
    val fiatMicros: Long,
    /** The pool's sell fee: the pool's `sellFeeBps`, else the house rate. Not read for Dollars. */
    val sellFeeBps: Int,
)

/** What [planCollectionFunding] decided. Every amount is in fiat micros of the request currency. */
data class CollectionFundingPlan(
    val payingCurrency: String,
    /** What the balances can't cover after fees, before any top-up. */
    val shortfallMicros: Long,
    /** 0, or at least the minimum top-up. */
    val topUpMicros: Long,
    /** The plan after any top-up, in funding order. */
    val legs: List<FundingLeg>,
) {
    val payableWithoutTopUp: Boolean get() = shortfallMicros == 0L
    val totalFeeMicros: Long get() = legs.sumOf { it.feeMicros }
}

data class FundingLeg(
    val source: String,
    /** False only for the paying currency's own leg. */
    val conversion: Boolean,
    /** The amount sold, fee included. A drained leg sells the whole on-chain balance. */
    val soldMicros: Long,
    val feeMicros: Long,
    /** What lands in the paying currency: [soldMicros] − [feeMicros]. */
    val deliveredMicros: Long,
    /** Set on the Dollars leg only. */
    val dollarsSplit: DollarsSplit? = null,
)

/** How much of a Dollars leg comes from the balance held before the top-up, and how much from it. */
data class DollarsSplit(val fromBalanceMicros: Long, val fromTopUpMicros: Long)

private class FundingOrder(val paying: String, val dollars: String) {

    /** The paying currency and Dollars spend at full balance; the rest lose their sell fee. */
    fun spendable(balance: FundingBalance): Long = when (balance.id) {
        paying, dollars -> balance.fiatMicros
        else -> balance.fiatMicros - sellFee(balance.fiatMicros, balance.sellFeeBps)
    }

    fun fund(amountMicros: Long, balances: List<FundingBalance>): List<FundingLeg> {
        val order = buildList {
            balances.firstOrNull { it.id == paying }?.let(::add)
            if (paying != dollars) balances.firstOrNull { it.id == dollars }?.let(::add)
            addAll(
                balances
                    .filter { it.id != paying && it.id != dollars }
                    .sortedWith(compareBy<FundingBalance> { it.fiatMicros }.thenBy { it.id }),
            )
        }

        val legs = mutableListOf<FundingLeg>()
        var remaining = amountMicros
        for (balance in order) {
            if (remaining <= 0) break
            val held = balance.fiatMicros
            if (held == 0L) continue

            if (balance.id == paying) {
                val used = minOf(held, remaining)
                legs += FundingLeg(balance.id, conversion = false, used, feeMicros = 0, used)
                remaining -= used
                continue
            }

            val bps = if (balance.id == dollars) 0 else balance.sellFeeBps
            val drainFee = sellFee(held, bps)
            // Drain first: at a half-micro fee tie, grossing up what is still needed lands a micro
            // under the balance, and selling that would leave the micro behind.
            val (sold, fee) = if (held - drainFee <= remaining) {
                held to drainFee
            } else {
                val gross = minOf(grossUp(remaining, bps), held)
                val grossFee = sellFee(gross, bps)
                check(gross - grossFee >= remaining) {
                    "a partial leg on ${balance.id} delivers ${gross - grossFee}, short of $remaining"
                }
                gross to grossFee
            }
            legs += FundingLeg(balance.id, conversion = true, sold, fee, sold - fee)
            remaining -= sold - fee
        }
        check(remaining <= 0) { "the plan is still short by $remaining after any top-up" }
        return legs
    }
}

private const val MICRO_DIGITS = 6
private const val BPS_DENOMINATOR = 10_000L

/** One minor unit of a currency with [minorDigits], in micros: 10_000 for USD's cent. */
internal fun minorUnitMicros(minorDigits: Int): Long {
    var unit = 1L
    repeat(MICRO_DIGITS - minorDigits) { unit *= 10 }
    return unit
}

/** `round(x × bps / 10_000)`, half-up: `launchpadSellFee` on micros. */
/**
 * Whether [micros] is under half of [unitMicros], the minor unit, so it displays as zero. Exactly
 * half is not dust: it rounds half-up to one minor unit.
 */
internal fun isDust(micros: Long, unitMicros: Long): Boolean = micros * 2 < unitMicros

internal fun sellFee(micros: Long, bps: Int): Long =
    divideHalfUp(micros * bps.coerceIn(0, BPS_DENOMINATOR.toInt()), BPS_DENOMINATOR)

/** `round(n / (1 − bps/10_000))`, half-up: `grossingUpLaunchpadSellFee` on micros. */
internal fun grossUp(micros: Long, bps: Int): Long {
    val capped = bps.coerceIn(0, BPS_DENOMINATOR.toInt())
    if (capped >= BPS_DENOMINATOR) return micros
    return divideHalfUp(micros * BPS_DENOMINATOR, BPS_DENOMINATOR - capped)
}

/** `round(num / den)`, half-up, for `num >= 0` and `den > 0`. */
internal fun divideHalfUp(num: Long, den: Long): Long {
    val quotient = num / den
    return if (2 * (num % den) >= den) quotient + 1 else quotient
}

internal fun ceilDiv(num: Long, den: Long): Long = (num + den - 1) / den
