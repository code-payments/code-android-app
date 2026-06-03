package com.getcode.opencode.internal.exchange

import com.flipcash.libs.currency.math.Estimator
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.units
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Fiat.FormattingRule
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.min
import com.getcode.services.opencode.BuildConfig
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealVerifiedFiatCalculator @Inject constructor(
    private val verifiedStateManager: VerifiedProtoManager,
    private val currencyController: CurrencyController,
) : VerifiedFiatCalculator {

    private val resolveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun resolveVerifiedState(
        currencyCode: CurrencyCode,
        mint: Mint,
    ): VerifiedState? {
        verifiedStateManager.getVerifiedStateFor(currencyCode, mint)?.let { cached ->
            val needsReserve = mint != Mint.usdf
            if (!needsReserve || cached.reserveProto != null) return cached
        }

        trace(
            tag = "VerifiedFiatCalculator",
            message = "live mint data is either expired or nonexistent. Fetching fresh",
            type = TraceType.Log,
        )

        currencyController.getLiveMintData(resolveScope, mint)
            .onFailure { cause ->
                trace(
                    tag = "VerifiedFiatCalculator",
                    message = "Live mint data fetch failed for $mint",
                    type = TraceType.Error,
                    error = cause
                )
            }

        return verifiedStateManager.getVerifiedStateFor(currencyCode, mint)
    }

    override suspend fun compute(
        amount: Fiat,
        token: Token,
        balance: Fiat?,
        rate: Rate,
        trace: Boolean,
    ): Result<VerifiedFiat> {
        val usdValue = amount.convertingToUsdIfNeeded(rate)
        // cap the entered amount as well, since our display rounds HALF_UP
        // e,g entered 0.02 USD, but balance is 0.016 USD
        // Balance may arrive in the user's local currency; normalize to USD
        // before comparing so the capped value preserves the USD currency code.
        val balanceInUsd = balance?.convertingToUsdIfNeeded(rate)
        val cappedValue = balanceInUsd?.let { min(it, usdValue) } ?: usdValue

        val verifiedState = resolveVerifiedState(rate.currency, token.address)

        if (token.address == Mint.usdf) {
            // this doesn't need a calculated value exchange since we are USDC
            val localFiat = if (rate.currency != CurrencyCode.USD) {
                LocalFiat.fromUsd(
                    usdf = cappedValue,
                    rate = rate,
                    mint = token.address,
                )
            } else {
                LocalFiat.fromUsd(usdf = cappedValue)
            }
            return Result.success(VerifiedFiat(localFiat, verifiedState))
        }

        if (verifiedState == null) {
            return Result.failure(ComputeVerifiedFiatError.StaleRate())
        }

        val verifiedSupply = verifiedState.reserveProto?.reserveState?.supplyFromBonding

        val supply = verifiedSupply ?: token.launchpadMetadata?.currentCirculatingSupplyQuarks ?: 0

        // determine quarks to exchange for the desired amount
        val valuation = Estimator.valueExchangeAsQuarks(
            valueInQuarks = cappedValue.quarks,
            currentSupplyInQuarks = supply,
            mintDecimals = 6, // usdf is 6 decimals
        ).getOrElse { return Result.failure(ComputeVerifiedFiatError.ComputationFailed(it)) }

        val (quarks, _) = valuation
        val units = quarks.units()
        val underlyingTokenAmount = Fiat(quarks = quarks.toLong(), currencyCode = CurrencyCode.USD)

        val sellEstimate = Fiat.tokenBalance(quarks.toLong(), token, supply).convertingTo(rate)
        if (!sellEstimate.hasDisplayableValue) {
            return Result.failure(ComputeVerifiedFiatError.AmountBelowMinimum())
        }

        val fx = sellEstimate.decimalValue.toBigDecimal().divideWithHighPrecision(units).toDouble()

        if (trace) {
            logExchange(
                rate = rate,
                amount = amount,
                usdValue = usdValue,
                balance = balance,
                cappedValue = cappedValue,
                token = token,
                supply = supply,
                quarks = quarks,
                units = units,
                fx = fx,
                sellEstimate = sellEstimate
            )
        }

        return Result.success(
            VerifiedFiat(
                localFiat = LocalFiat(
                    underlyingTokenAmount = underlyingTokenAmount,
                    // our native amount for the transfer is the valuation of the quarks from a sell
                    nativeAmount = sellEstimate,
                    mint = token.address,
                    rate = Rate(fx = fx, currency = rate.currency),
                ),
                verifiedState = verifiedState,
            )
        )
    }

    private fun logExchange(
        rate: Rate,
        amount: Fiat,
        usdValue: Fiat,
        balance: Fiat?,
        cappedValue: Fiat,
        token: Token,
        supply: Long,
        quarks: BigDecimal,
        units: BigDecimal,
        fx: Double,
        sellEstimate: Fiat,
    ) {
        trace(
            tag = "VerifiedFiatCalculator",
            message = "currency exchange",
            metadata = {
                "requested currency" to rate.currency.name
                "original currency fx" to rate.fx
                "requested amount" to amount.formatted()
                "requested quarks (in USD)" to usdValue.quarks * 1_000_000
                "balance quarks (in USD)" to balance?.quarks?.times(1_000_000)
                "capped quarks (in USD)" to cappedValue.quarks * 1_000_000
                "supply of ${token.symbol}" to supply
                "calculated quarks" to quarks
                "units" to units
                "fx" to fx
                "sell estimate" to sellEstimate.formatted(rule = FormattingRule.Length(token.decimals))
            }
        )
    }
}
