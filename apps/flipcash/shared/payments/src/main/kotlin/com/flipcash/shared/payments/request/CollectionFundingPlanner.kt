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
        return plan(amount, paying, balances(rate))
    }

    fun plan(amount: Fiat, paying: Mint, balances: List<FundingBalance>): CollectionFundingPlan =
        planCollectionFunding(
            amountMicros = amount.quarks,
            minorDigits = amount.currencyCode.fractionDigits,
            minTopUpMicros = MIN_TOP_UP_USD_MICROS,
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

    private companion object {
        /**
         * The planner needs a minimum to size a top-up. S4 never tops up, so this only shapes the
         * short state's numbers; S5 takes it from the on-ramp.
         */
        const val MIN_TOP_UP_USD_MICROS = 5_000_000L
    }
}
