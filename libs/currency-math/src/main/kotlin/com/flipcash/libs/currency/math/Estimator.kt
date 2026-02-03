package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintDecimals
import com.flipcash.libs.currency.math.internal.curves.ContinuousBondingCurve
import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import java.math.BigDecimal
import java.math.RoundingMode

object Estimator {

    private val DefaultCurveType = CurveType.Discrete

    private fun getCurve(curveType: CurveType): BondingCurve {
        return when (curveType) {
            CurveType.Continuous -> ContinuousBondingCurve.getOrThrow()
            CurveType.Discrete -> DiscreteBondingCurve.getOrThrow()
        }
    }

    /**
     * Calculates the current spot price of a token based on its total supply.
     *
     * This function uses an exponential bonding curve to determine the instantaneous price
     * at a specific point of the total token supply. The calculation involves scaling the
     * supply from its smallest unit ("quarks") to its standard decimal representation before
     * querying the curve.
     *
     * @param currentSupplyInQuarks The total supply of the token, expressed in its smallest
     * unit ("quarks").
     * @return A [Result] containing the spot price as a [BigDecimal] on success, or an
     * exception on failure.
     */
    fun currentPriceFor(
        currentSupplyInQuarks: Long,
        curveType: CurveType = DefaultCurveType,
    ): Result<BigDecimal> {
        return runCatching {
            val scale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val unscaledCurrentSupply = BigDecimal(currentSupplyInQuarks, mc)
            val scaledCurrentSupply = unscaledCurrentSupply.divide(scale, mc)
            scaledCurrentSupply
        }.fold(
            onSuccess = { scaledCurrentSupply ->
                val curve = getCurve(curveType)
                curve.spotPriceAtSupply(scaledCurrentSupply)
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }

    /**
     * Estimates the number of tokens that can be obtained by exchanging a certain value.
     *
     * This function models a "value exchange" on the bonding curve, which is mathematically
     * equivalent to a "buy" operation without any fees. It calculates how many tokens
     * would be minted for a given input value, based on the current state of the curve.
     *
     * The process involves:
     * 1. Scaling the input `valueInQuarks` from its smallest unit to its standard decimal representation
     *    using `mintDecimals`.
     * 2. Scaling the `currentSupplyInQuarks` of the token to its standard decimal representation.
     * 3. Using the exponential curve model (`ExponentialCurve.tokensForValueExchange`) to determine
     *    the quantity of tokens that correspond to the input value.
     * 4. Scaling the result back to its "quark" representation (smallest indivisible unit).
     *
     * @param valueInQuarks The amount of value being exchanged, expressed in the value token's smallest unit (e.g., lamports for SOL, or the smallest unit for USDF).
     * @param currentSupplyInQuarks The current total supply of the token, in quarks.
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDF typically has 6).
     * @return A [Result] containing the estimated number of quarks to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsQuarks(
        valueInQuarks: Long,
        currentSupplyInQuarks: Long,
        mintDecimals: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<Valuation.Quarks> {
        return runCatching {
            val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val valuation = valueExchangeAsTokens(
                valueInQuarks = valueInQuarks,
                currentSupplyInQuarks = currentSupplyInQuarks,
                mintDecimals = mintDecimals,
                curveType = curveType
            ).getOrThrow()
            val unscaledTokens = valuation.tokens.multiply(tokenScale, mc)
            val fx = valueInQuarks.toBigDecimal().divideWithHighPrecision(unscaledTokens)
            Valuation.Quarks(
                quarks = unscaledTokens,
                fx = fx
            )
        }
    }

    /**
     * Estimates the number of tokens that can be obtained by exchanging a certain value.
     *
     * This function models a "value exchange" on the bonding curve, which is mathematically
     * equivalent to a "buy" operation without any fees. It calculates how many tokens
     * would be minted for a given input value, based on the current state of the curve.
     *
     * The process involves:
     * 1. Scaling the input `valueInQuarks` from its smallest unit to its standard decimal representation
     *    using `mintDecimals`.
     * 2. Scaling the `currentSupplyInQuarks` of the token to its standard decimal representation.
     * 3. Using the exponential curve model (`ExponentialCurve.tokensForValueExchange`) to determine
     *    the quantity of tokens that correspond to the input value.
     *
     * @param valueInQuarks The amount of value being exchanged, expressed in the value token's smallest unit (e.g., lamports for SOL, or the smallest unit for USDF).
     * @param currentSupplyInQuarks The current total supply of the token, in quarks.
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDF typically has 6).
     * @return A [Result] containing the estimated number of tokens to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsTokens(
        valueInQuarks: Long,
        currentSupplyInQuarks: Long,
        mintDecimals: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<Valuation.Tokens> {
        return runCatching {
            val curve = getCurve(curveType)
            when (curveType) {
                CurveType.Continuous -> {
                    val valueScale = BigDecimal.TEN.pow(mintDecimals, mc)
                    val unscaledValue = BigDecimal(valueInQuarks)
                    val scaledValue = unscaledValue.divide(valueScale, mc)

                    val tokenScale = BigDecimal.TEN.pow(mintDecimals, mc)
                    val unscaledCurrentValue = BigDecimal(currentSupplyInQuarks)
                    val scaledCurrentValue = unscaledCurrentValue.divide(tokenScale, mc)

                    curve.tokensForValueExchange(scaledCurrentValue, scaledValue).getOrThrow()
                }
                CurveType.Discrete -> {
                    // Convert supply quarks to whole tokens
                    val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
                    val currentSupply = BigDecimal(currentSupplyInQuarks).divideWithHighPrecision(tokenScale)

                    // Calculate TVL from supply (value of all tokens from 0 to currentSupply)
                    val currentValue = curve.tokensToValue(
                        currentSupply = BigDecimal.ZERO,
                        tokens = currentSupply
                    ).getOrThrow()

                    // Convert input value to USDF
                    val valueScale = BigDecimal.TEN.pow(mintDecimals, mc)
                    val scaledValue = BigDecimal(valueInQuarks).divideWithHighPrecision(valueScale)

                    curve.tokensForValueExchange(
                        value = scaledValue,
                        currentValue = currentValue,
                    ).getOrThrow()
                }
            }
        }
    }

    /**
     * Estimates the result of a buy transaction on the bonding curve.
     *
     * This function calculates how many tokens a user will receive for a given amount of the base currency,
     * accounting for the current token supply and a specified fee. All currency values are handled in their
     * smallest denomination (quarks).
     *
     * @param amountInQuarks The amount of base currency being spent to buy tokens, in quarks.
     * @param currentSupplyInQuarks The current total supply of the token, in quarks.
     * @param mintDecimals The number of decimal places for the base currency being spent.
     * @param feeBps The transaction fee in basis points (1 basis point = 0.01%).
     * @return A [Result] containing a [BuyEstimation] on success, which includes the net tokens to be received
     * and the calculated fees, both in quarks. On failure, it returns a [Result] containing the exception.
     */
    fun buy(
        amountInQuarks: Long,
        currentSupplyInQuarks: Long,
        mintDecimals: Int,
        feeBps: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<BuyEstimation> {
        return runCatching {
            val curve = getCurve(curveType)
            require(amountInQuarks > 0) { "Amount must be positive" }
            require(currentSupplyInQuarks >= 0) { "Current supply must be non-negative" }
            require(feeBps >= 0) { "Fee basis points must be non-negative" }

            when (curveType) {
                CurveType.Continuous -> {
                    val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
                    val amountScale = BigDecimal.TEN.pow(mintDecimals, mc)

                    val unscaledBuyAmount = BigDecimal(amountInQuarks, mc)
                    val scaledBuyAmount = unscaledBuyAmount.divideWithHighPrecision(amountScale)

                    val unscaledCurrentSupply = BigDecimal(currentSupplyInQuarks, mc)
                    val scaledCurrentSupply = unscaledCurrentSupply.divideWithHighPrecision(tokenScale)

                    val scaledTokens =
                        curve.valueToTokens(scaledCurrentSupply, scaledBuyAmount).getOrThrow()
                    val unscaledTokens = scaledTokens.multiplyWithHighPrecision(tokenScale)

                    val feePctValue = BigDecimal(feeBps).divideWithHighPrecision(BigDecimal("10000"))
                    val scaledFees = scaledTokens.multiplyWithHighPrecision(feePctValue)
                    val unscaledFeesBD =
                        scaledFees.multiplyWithHighPrecision(tokenScale).setScale(0, RoundingMode.DOWN)
                    val unscaledFeesQuarks = unscaledFeesBD.longValueExact()

                    val netTokensBD =
                        unscaledTokens.subtract(unscaledFeesBD, mc).setScale(0, RoundingMode.DOWN)
                    val netTokensQuarks = netTokensBD.longValueExact()

                    BuyEstimation(
                        netTokensToReceive = netTokensQuarks.toBigDecimal(),
                        fees = unscaledFeesQuarks.toBigDecimal(),
                    )
                }
                CurveType.Discrete -> {
                    // Convert USDF quarks to USDF units
                    val usdfValue = BigDecimal(amountInQuarks).divideWithHighPrecision(1_000_000.toBigDecimal())

                    // Convert supply quarks to whole tokens
                    val quarksPerToken = BigDecimal.TEN.pow(mintDecimals, mc)
                    val currentSupply = BigDecimal(currentSupplyInQuarks).divideWithHighPrecision(quarksPerToken)

                    // Calculate tokens bought
                    val grossTokens = curve.valueToTokens(currentSupply, usdfValue).getOrThrow()

                    // Apply fee
                    val feeMultiplier = BigDecimal(feeBps).divideWithHighPrecision(BigDecimal("10000"))
                    val fees = grossTokens.multiplyWithHighPrecision(feeMultiplier)
                    val netTokens = grossTokens.subtractWithHighPrecision(fees)

                    BuyEstimation(
                        netTokensToReceive = netTokens,
                        fees = fees,
                    )
                }
            }

        }
    }

    /**
     * Calculates the estimated value received from selling a specified amount of tokens,
     * accounting for fees. This function operates on the bonding curve to determine the
     * exchange value.
     *
     * The calculation involves:
     * 1. Scaling the input token amount and the current market value from their "quark" representations
     *    (smallest indivisible unit) to their standard decimal representations.
     * 2. Using the exponential curve model (`ExponentialCurve.valueFromSellingTokens`) to determine the
     *    gross value received for selling the specified number of tokens.
     * 3. Calculating the fee based on the gross value and the provided fee basis points (BPS).
     * 4. Subtracting the fee from the gross value to get the net amount the seller will receive.
     * 5. Returning the net amount and the fee, both in "quarks".
     *
     * @param amountInQuarks The amount of the token to be sold, expressed in its smallest unit ("quarks").
     * @param marketState The current state of the market, which can be based on total value locked
     *        (`MarketState.FromValue`) or current circulating supply (`MarketState.FromSupply`). The required
     *        type depends on the `curveType`.
     * @param outputDecimals The number of decimal places for the value token (e.g., USDF, SOL).
     * @param feeBps The fee percentage expressed in basis points (1 BPS = 0.01%). For example, 50 BPS is a 0.5% fee.
     * @return A [Result] wrapper containing a [SellEstimation] on success, which includes the
     *         `netAmountToReceive` and `fees` in "quarks". Returns a `Result.failure` if any
     *         part of the calculation fails (e.g., due to invalid inputs or math errors).
     */
    fun sell(
        amountInQuarks: Long,
        marketState: MarketState,
        mintDecimals: Int,
        outputDecimals: Int,
        feeBps: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<SellEstimation> {
        return runCatching {
            val curve = getCurve(curveType)
            require(amountInQuarks >= 0) { "Amount must be positive" }
            require(marketState.amount >= 0) { "Current market state value must be non-negative" }
            require(feeBps >= 0) { "Fee basis points must be non-negative" }

            if (amountInQuarks == 0L) {
                return@runCatching SellEstimation(
                    netAmountToReceive = BigDecimal.ZERO,
                    fees = BigDecimal.ZERO,
                )
            }

            when (curveType) {
                CurveType.Continuous -> {
                    require(marketState is MarketState.FromValue) { "FromValue is required for Continuous curve" }
                    val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
                    val unscaledSellAmount = BigDecimal(amountInQuarks, mc)
                    val scaledSellAmount = unscaledSellAmount.divideWithHighPrecision(tokenScale)

                    val valueScale = BigDecimal.TEN.pow(outputDecimals, mc)
                    val unscaledCurrentValue = BigDecimal(marketState.valueInQuarks, mc)
                    val scaledCurrentValue = unscaledCurrentValue.divideWithHighPrecision(valueScale)

                    val scaledValue =
                        curve.tokensToValue(scaledCurrentValue, scaledSellAmount).getOrThrow()
                    val unscaledValueBD = scaledValue.multiplyWithHighPrecision(valueScale)

                    val feePctValue = BigDecimal(feeBps).divideWithHighPrecision(BigDecimal("10000"))
                    val scaledFees = scaledValue.multiplyWithHighPrecision(feePctValue)
                    val unscaledFeesBD =
                        scaledFees.multiplyWithHighPrecision(valueScale).setScale(0, RoundingMode.DOWN)
                    val unscaledFeesUsd = unscaledFeesBD.longValueExact()
                        .toBigDecimal().divideWithHighPrecision(BigDecimal(1_000_000))

                    val netAmountBD =
                        unscaledValueBD.subtract(unscaledFeesBD, mc).setScale(0, RoundingMode.DOWN)
                    val netAmountUsdf = netAmountBD.longValueExact()
                        .toBigDecimal().divideWithHighPrecision(BigDecimal(1_000_000))

                    SellEstimation(
                        netAmountToReceive = netAmountUsdf,
                        fees = unscaledFeesUsd,
                    )
                }
                CurveType.Discrete -> {
                    require(marketState is MarketState.FromSupply) { "FromSupply is required for Discrete curve" }
                    // Convert token quarks to whole tokens
                    val quarksPerToken = BigDecimal.TEN.pow(mintDecimals)
                    val tokensToSell = BigDecimal(amountInQuarks).divideWithHighPrecision(quarksPerToken)

                    val currentSupply = BigDecimal(marketState.supplyInQuarks)
                        .divideWithHighPrecision(quarksPerToken)

                    // if the balance exceeds the supply, then assume the supply is the balance
                    val effectiveSell = tokensToSell.coerceIn(BigDecimal.ZERO, currentSupply)

                    val supplyAfter = currentSupply - effectiveSell

                    val grossUSDF = curve.tokensToValue(
                        currentSupply = supplyAfter,
                        tokens = tokensToSell
                    ).getOrThrow()

                    // Apply fee
                    val feeMultiplier = BigDecimal(feeBps).divideWithHighPrecision(BigDecimal("10000"))
                    val fees = grossUSDF.multiplyWithHighPrecision(feeMultiplier)
                    val netUSDF = grossUSDF.subtractWithHighPrecision(fees)

                    SellEstimation(
                        netAmountToReceive = netUSDF,
                        fees = fees,
                    )
                }
            }
        }
    }

    fun currentMarketCap(
        currentSupplyInQuarks: Long,
        curveType: CurveType = DefaultCurveType,
    ): Result<BigDecimal> {
        return runCatching {
            val pricePerToken = currentPriceFor(currentSupplyInQuarks, curveType).getOrThrow()
            val tokens = currentSupplyInQuarks.toBigDecimal()
                .movePointLeft(DefaultMintDecimals)

            tokens.multiply(pricePerToken)
        }
    }
}

data class BuyEstimation(
    val netTokensToReceive: BigDecimal,
    val fees: BigDecimal,
)

data class SellEstimation(
    val netAmountToReceive: BigDecimal,
    val fees: BigDecimal,
)

sealed interface Valuation {
    val fx: BigDecimal

    data class Tokens(
        val tokens: BigDecimal,
        override val fx: BigDecimal,
    ) : Valuation {
        companion object {
            val Zero = Tokens(BigDecimal.ZERO, BigDecimal.ZERO)
        }
    }

    data class Quarks(
        val quarks: BigDecimal,
        override val fx: BigDecimal,
    ) : Valuation
}

sealed interface MarketState {
    val amount: Long
    data class FromValue(val valueInQuarks: Long) : MarketState {
        override val amount: Long = valueInQuarks
    }
    data class FromSupply(val supplyInQuarks: Long) : MarketState {
        override val amount: Long = supplyInQuarks
    }
}