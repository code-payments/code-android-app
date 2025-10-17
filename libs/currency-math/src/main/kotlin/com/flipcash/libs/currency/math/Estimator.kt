package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintDecimals
import java.math.BigDecimal

object Estimator {
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
    fun currentPriceFor(currentSupplyInQuarks: Long): Result<BigDecimal> {
        return runCatching {
            val scale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val unscaledCurrentSupply = BigDecimal(currentSupplyInQuarks, mc)
            val scaledCurrentSupply = unscaledCurrentSupply.divide(scale, mc)
            scaledCurrentSupply
        }.fold(
            onSuccess = { scaledCurrentSupply ->
                val curve = ExponentialCurve.getOrThrow()
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
     * @param currentSupplyInQuarks The current total supply of the token, expressed in its smallest unit ("quarks").
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDC typically has 6).
     * @return A [Result] containing the estimated number of quarks to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsQuarks(
        valueInQuarks: Long,
        currentSupplyInQuarks: Long,
        mintDecimals: Int,
    ): Result<BigDecimal> {
        return runCatching {
            val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val tokens = valueExchangeAsTokens(valueInQuarks, currentSupplyInQuarks, mintDecimals).getOrThrow()
            val unscaledTokens = tokens.multiply(tokenScale, mc)
            unscaledTokens
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
     * @param currentSupplyInQuarks The current total supply of the token, expressed in its smallest unit ("quarks").
     * @param mintDecimals The number of decimal places for the value token (e.g., SOL has 9, USDC typically has 6).
     * @return A [Result] containing the estimated number of tokens to be received as a [BigDecimal] on success.
     *         On failure, it returns a `Result.failure` wrapping the exception.
     */
    fun valueExchangeAsTokens(
        valueInQuarks: Long,
        currentSupplyInQuarks: Long,
        mintDecimals: Int,
    ): Result<BigDecimal> {
        return runCatching {
            val curve = ExponentialCurve.getOrThrow()
            val valueScale = BigDecimal.TEN.pow(mintDecimals, mc)
            val unscaledValue = BigDecimal("$valueInQuarks")
            val scaledValue = unscaledValue.divide(valueScale, mc)

            val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val unscaledCurrentSupply = BigDecimal("$currentSupplyInQuarks")
            val scaledCurrentSupply = unscaledCurrentSupply.divide(tokenScale, mc)

            curve.tokensForValueExchange(scaledCurrentSupply, scaledValue).getOrThrow()
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
    ): Result<BuyEstimation> {
        return runCatching {
            val curve = ExponentialCurve.getOrThrow()
            val tokenScale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val amountScale = BigDecimal.TEN.pow(mintDecimals, mc)

            val unscaledBuyAmount = BigDecimal(amountInQuarks, mc)
            val scaledBuyAmount = unscaledBuyAmount.divide(amountScale, mc)

            val unscaledCurrentSupply = BigDecimal(currentSupplyInQuarks, mc)
            val scaledCurrentSupply = unscaledCurrentSupply.divide(tokenScale, mc)

            val scaledTokens = curve.tokensBoughtForValue(scaledCurrentSupply, scaledBuyAmount).getOrThrow()
            val unscaledTokens = scaledTokens.multiply(tokenScale, mc)

            val feePctValue = BigDecimal(feeBps).divide(BigDecimal("10000"), mc)
            val scaledFees = scaledTokens.multiply(feePctValue, mc)
            val unscaledFees = scaledFees.multiply(tokenScale, mc)

            val netTokens = unscaledTokens.subtract(unscaledFees, mc)
            val tokensQuarks = netTokens
            val feesQuarks = unscaledFees

            BuyEstimation(
                netTokensToReceive = tokensQuarks,
                fees = feesQuarks,
            )
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
    ): Result<SellEstimation> {
        return runCatching {
            val curve = ExponentialCurve.getOrThrow()

            val scale = BigDecimal.TEN.pow(DefaultMintDecimals, mc)
            val unscaledSellAmount = BigDecimal(amountInQuarks, mc)
            val scaledSellAmount = unscaledSellAmount.divide(scale, mc)

            val tokenScale = BigDecimal.TEN.pow(mintDecimals, mc)
            val unscaledCurrentValue = BigDecimal(currentValueInQuarks, mc)
            val scaledCurrentValue = unscaledCurrentValue.divide(tokenScale, mc)


            val scaledTokens = curve.valueFromSellingTokens(scaledCurrentValue, scaledSellAmount).getOrThrow()
            val unscaledTokens = scaledTokens.multiply(tokenScale, mc)

            val feePctValue = BigDecimal(feeBps).divide(BigDecimal("10000"), mc)
            val scaledFees = scaledTokens.multiply(feePctValue, mc)
            val unscaledFees = scaledFees.multiply(tokenScale, mc)

            val netAmount = unscaledTokens.subtract(unscaledFees, mc)
            val amountQuarks = netAmount
            val feesQuarks = unscaledFees

            SellEstimation(
                netAmountToReceive = amountQuarks,
                fees = feesQuarks,
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