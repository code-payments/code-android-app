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
     * @param valueInQuarks The amount of value being exchanged, expressed in the value token's smallest unit (e.g., lamports for SOL, or the smallest unit for USDC).
     * @param currentValueInQuarks The current total value locked in the bonding curve for this token,
     *                             expressed in the value token's smallest unit ("quarks").
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDC typically has 6).
     * @return A [Result] containing the estimated number of quarks to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsQuarks(
        valueInQuarks: Long,
        currentValueInQuarks: Long,
        mintDecimals: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<Valuation.Quarks> {
        return runCatching {
            val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val valuation = valueExchangeAsTokens(
                valueInQuarks = valueInQuarks,
                currentValueInQuarks = currentValueInQuarks,
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
     * @param valueInQuarks The amount of value being exchanged, expressed in the value token's smallest unit (e.g., lamports for SOL, or the smallest unit for USDC).
     * @param currentValueInQuarks The current total value locked in the bonding curve for this token,
     *                             expressed in the value token's smallest unit ("quarks").
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDC typically has 6).
     * @return A [Result] containing the estimated number of tokens to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsTokens(
        valueInQuarks: Long,
        currentValueInQuarks: Long,
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
                    val unscaledCurrentValue = BigDecimal(currentValueInQuarks)
                    val scaledCurrentValue = unscaledCurrentValue.divide(tokenScale, mc)

                    curve.tokensForValueExchange(scaledCurrentValue, scaledValue).getOrThrow()
                }
                CurveType.Discrete -> {
                    val valueScale = BigDecimal.TEN.pow(mintDecimals, mc)
                    val scaledValue = BigDecimal(valueInQuarks).divideWithHighPrecision(valueScale)
                    val scaledCurrentValue = BigDecimal(currentValueInQuarks).divideWithHighPrecision(valueScale)

                    curve.tokensForValueExchange(
                        value = scaledValue,
                        currentValue = scaledCurrentValue,
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
                    // Convert USDC quarks to USDC units
                    val usdcValue = BigDecimal(amountInQuarks).divideWithHighPrecision(1_000_000.toBigDecimal())

                    // Convert supply quarks to whole tokens
                    val quarksPerToken = BigDecimal.TEN.pow(mintDecimals, mc)
                    val currentSupply = BigDecimal(currentSupplyInQuarks).divideWithHighPrecision(quarksPerToken)

                    // Calculate tokens bought
                    val grossTokens = curve.valueToTokens(currentSupply, usdcValue).getOrThrow()

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
     * @param currentValueInQuarks The current total value locked in the bonding curve for this token,
     *                             expressed in the value token's smallest unit ("quarks").
     * @param mintDecimals The number of decimal places for the value token (e.g., USDC, SOL).
     * @param feeBps The fee percentage expressed in basis points (1 BPS = 0.01%). For example, 50 BPS is a 0.5% fee.
     * @return A [Result] wrapper containing a [SellEstimation] on success, which includes the
     *         `netAmountToReceive` and `fees` in "quarks". Returns a `Result.failure` if any
     *         part of the calculation fails (e.g., due to invalid inputs or math errors).
     */
    fun sell(
        amountInQuarks: Long,
        currentValueInQuarks: Long,
        mintDecimals: Int,
        feeBps: Int,
        curveType: CurveType = DefaultCurveType,
    ): Result<SellEstimation> {
        return runCatching {
            val curve = getCurve(curveType)
            require(amountInQuarks > 0) { "Amount must be positive" }
            require(currentValueInQuarks >= 0) { "Current value must be non-negative" }
            require(feeBps >= 0) { "Fee basis points must be non-negative" }

            println("amountInQuarks: $amountInQuarks, currentValueInQuarks: $currentValueInQuarks,")
            when (curveType) {
                CurveType.Continuous -> {
                    val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
                    val unscaledSellAmount = BigDecimal(amountInQuarks, mc)
                    val scaledSellAmount = unscaledSellAmount.divideWithHighPrecision(tokenScale)

                    val valueScale = BigDecimal.TEN.pow(mintDecimals, mc)
                    val unscaledCurrentValue = BigDecimal(currentValueInQuarks, mc)
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

                    // Get current supply from TVL
                    val currentSupply = curve.supplyFromValue(scaledCurrentValue).getOrThrow()

                    // For selling: calculate value at (supply - tokens) going up to supply
                    val supplyAfterSell = currentSupply.subtract(scaledSellAmount)

                    val netAmountBD =
                        unscaledValueBD.subtract(unscaledFeesBD, mc).setScale(0, RoundingMode.DOWN)
                    val netAmountUsdc = netAmountBD.longValueExact()
                        .toBigDecimal().divideWithHighPrecision(BigDecimal(1_000_000))

                    println("tokensToSell: $scaledSellAmount, currentValue: $scaledCurrentValue, currentSupply: $currentSupply, supplyAfterSell: $supplyAfterSell")

                    println("gross: $unscaledValueBD, fees: $unscaledFeesUsd, net: $netAmountBD")

                    SellEstimation(
                        netAmountToReceive = netAmountUsdc,
                        fees = unscaledFeesUsd,
                    )
                }
                CurveType.Discrete -> {
                    // Convert token quarks to whole tokens
                    val quarksPerToken = BigDecimal.TEN.pow(DefaultMintDecimals)
                    val tokensToSell = BigDecimal(amountInQuarks).divideWithHighPrecision(quarksPerToken)

                    // Convert value quarks to dollars
                    val valueScale = BigDecimal.TEN.pow(mintDecimals)
                    val currentValue = BigDecimal(currentValueInQuarks).divideWithHighPrecision(valueScale)

                    // Get current supply from TVL
                    val currentSupply = curve.supplyFromValue(currentValue).getOrThrow()

                    // For selling: calculate value at (supply - tokens) going up to supply
                    val supplyAfterSell = currentSupply.subtract(tokensToSell)

                    println("tokensToSell: $tokensToSell, currentValue: $currentValue, currentSupply: $currentSupply, supplyAfterSell: $supplyAfterSell")

                    val grossUSDC = curve.tokensToValue(
                        currentSupply = supplyAfterSell,
                        tokens = tokensToSell
                    ).getOrThrow()

                    // Apply fee
                    val feeMultiplier = BigDecimal(feeBps).divideWithHighPrecision(BigDecimal("10000"))
                    val fees = grossUSDC.multiplyWithHighPrecision(feeMultiplier)
                    val netUSDC = grossUSDC.subtractWithHighPrecision(fees)

                    println("gross: $grossUSDC, fees: $fees, net: $netUSDC")
                    SellEstimation(
                        netAmountToReceive = netUSDC,
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
            val spotPrice = currentPriceFor(currentSupplyInQuarks, curveType).getOrThrow()
            (currentSupplyInQuarks.toBigDecimal() * spotPrice)
                .divideWithHighPrecision(BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            )
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