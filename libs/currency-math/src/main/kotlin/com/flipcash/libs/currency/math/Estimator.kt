package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintDecimals
import java.math.BigDecimal
import java.math.BigInteger

object Estimator {
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

    fun valueExchange(
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

            val scaledTokens = curve.tokensForValueExchange(scaledCurrentSupply, scaledValue).getOrThrow()
            val unscaledTokens = scaledTokens.multiply(tokenScale, mc)

            unscaledTokens
        }
    }

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