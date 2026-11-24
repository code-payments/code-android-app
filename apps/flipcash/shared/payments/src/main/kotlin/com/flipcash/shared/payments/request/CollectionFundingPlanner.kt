package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.FundingBalance
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.planCollectionFunding
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.sellFeeBpsOrHouseRate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Turns the wallet into the funding planner's input and runs it. The pay sheet's gate, its fee
 * line and [CollectionFunder] all plan through here, so what the sheet promises is what funding
 * converts.
 *
 * Planner ids are mint addresses in base58.
 */
class CollectionFundingPlanner @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
) {

    /**
     * Plans paying [amount] in [paying] from the balances as they stand, or null with no rate for
     * the request's currency.
     */
    fun plan(amount: Fiat, paying: Mint): CollectionFundingPlan? {
        val rate = exchange.rateFor(amount.currencyCode) ?: return null
        return plan(amount, paying, balances(rate), rate)
    }

    fun plan(amount: Fiat, paying: Mint, balances: List<FundingBalance>, rate: Rate): CollectionFundingPlan =
        planCollectionFunding(
            amountMicros = amount.quarks,
            minorDigits = amount.currencyCode.fractionDigits,
            minTopUpMicros = minTopUpMicros(rate, amount.currencyCode.fractionDigits),
            dollarsId = Mint.usdf.base58(),
            // The caller has already chosen: the pick in this sheet, else the last paid currency,
            // else Dollars.
            selected = paying.base58(),
            lastPaid = null,
            balances = balances,
        )

    /**
     * Every held balance, valued in [rate]'s currency at full precision. The wallet keeps balances
     * in USD micros, so this multiplies by the rate once and rounds to the micro.
     */
    fun balances(rate: Rate): List<FundingBalance> =
        tokenCoordinator.cachedTokens().mapNotNull { token ->
            val usd = tokenCoordinator.currentBalance(token.address)
            if (usd.quarks <= 0L) return@mapNotNull null
            FundingBalance(
                id = token.address.base58(),
                fiatMicros = (usd.quarks * rate.fx).roundToLong(),
                sellFeeBps = token.launchpadMetadata.sellFeeBpsOrHouseRate,
            )
        }

    /**
     * What to order for [plan]'s top-up, in USD cents: the top-up in the request's currency
     * converted back at [rate] and rounded up, so what lands covers the plan. Null without one.
     */
    fun topUpUsdCents(plan: CollectionFundingPlan, rate: Rate): Long? {
        if (plan.topUpMicros <= 0L) return null
        val usdMicros = BigDecimal(plan.topUpMicros).divide(BigDecimal.valueOf(rate.fx), 0, RoundingMode.CEILING)
        return usdMicros.divide(BigDecimal(USD_MICROS_PER_CENT), 0, RoundingMode.CEILING).toLong()
    }

    internal companion object {
        /** Google Pay's smallest top-up through Coinbase guest checkout: $5.00. */
        const val MIN_TOP_UP_USD_MICROS = 5_000_000L
        private const val USD_MICROS_PER_CENT = 10_000L

        /**
         * The $5.00 minimum in [rate]'s currency, rounded up to its minor unit, as the spec has the
         * caller convert it (group-collections spec, "Funding a payment").
         */
        fun minTopUpMicros(rate: Rate, minorDigits: Int): Long {
            val micros = BigDecimal(MIN_TOP_UP_USD_MICROS).multiply(BigDecimal.valueOf(rate.fx))
            val unit = BigDecimal.TEN.pow(MICRO_DIGITS - minorDigits.coerceIn(0, MICRO_DIGITS))
            return micros.divide(unit, 0, RoundingMode.CEILING).multiply(unit).toLong()
        }

        private const val MICRO_DIGITS = 6
    }
}
