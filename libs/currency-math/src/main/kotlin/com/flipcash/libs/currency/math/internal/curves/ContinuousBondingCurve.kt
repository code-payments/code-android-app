package com.flipcash.libs.currency.math.internal.curves

import android.annotation.SuppressLint
import com.flipcash.libs.currency.math.BondingCurve
import com.flipcash.libs.currency.math.Valuation
import com.flipcash.libs.currency.math.formatter
import com.flipcash.libs.currency.math.internal.exp
import com.flipcash.libs.currency.math.internal.ln
import com.flipcash.libs.currency.math.mc
import java.math.BigDecimal

// Constants for the default curve from $0.01 to $1_000_000 over 21_000_000 tokens
private val CURVE_A = BigDecimal("11400.230149967394933471")
private val CURVE_B = BigDecimal("0.000000877175273521")
private val CURVE_C = CURVE_B

@SuppressLint("VisibleForTests")
internal class ContinuousBondingCurve(
    val a: BigDecimal,
    val b: BigDecimal,
    val c: BigDecimal
) : BondingCurve {
    companion object {
        fun getOrThrow(): ContinuousBondingCurve {
            return ContinuousBondingCurve(CURVE_A, CURVE_B, CURVE_C)
        }
    }

    /**
     * Calculate token price at a given supply.
     * This function computes the spot price of a token based on the current supply
     * using the formula: R'(S) = a * b * e^(c * S)
     * where:
     * - R'(S) is the spot price at supply S
     * - S is the current supply of the token
     * - a, b, c are constants defining the curve's shape
     *
     * @param supply The current supply of the token.
     * @return A [Result] containing the ccalculated spot price as a BigDecimal, or an exception if an arithmetic error occurs.
     */
    override fun spotPriceAtSupply(supply: BigDecimal): Result<BigDecimal> {
        return runCatching {
            val cTimesS = c.multiply(supply, mc)
            val exp = cTimesS.exp(mc)
            a.multiply(b, mc).multiply(exp, mc)
        }
    }

    /**
     * Calculate the cost to buy a certain number of tokens given the current supply.
     * This function answers the question: "How much does it cost to buy X tokens?"
     * The formula used is: `(a * b / c) * (e^(c * new_supply) - e^(c * current_supply))`
     * where `new_supply = current_supply + tokensToBuy`.
     *
     * @param currentSupply The current supply of tokens.
     * @param tokensToBuy The number of tokens to be purchased.
     * @return A [Result] containing the calculated cost to buy the tokens, or an exception if an arithmetic error occurs.
     */
    private fun costToBuyTokens(
        currentSupply: BigDecimal,
        tokensToBuy: BigDecimal,
    ): Result<BigDecimal> {
        return runCatching {
            val newSupply = currentSupply.add(tokensToBuy)
            val cs = c.multiply(currentSupply, mc)
            val ns = c.multiply(newSupply, mc)
            val expCs = cs.exp(mc)
            val expNs = ns.exp(mc)
            val abOverC = a.multiply(b, mc).divide(c, mc)
            val diff = expNs.subtract(expCs, mc)
            abOverC.multiply(diff, mc)
        }
    }


    /**
     * Calculate the value received when selling `tokensToSell` tokens.
     * This function answers the question: "How much value can I get for X tokens?"
     * The formula used is: `(a * b / c) * (e^(c * current_supply) - e^(c * new_supply))`
     * where `new_supply = current_supply - tokensToSell`.
     *
     * @param currentValue The current value before selling any tokens.
     * @param tokens The number of tokens to be sold.
     * @return A [Result] containing the calculated value received from selling the tokens, or an exception if an arithmetic error occurs.
     */
    override fun tokensToValue(
        currentSupply: BigDecimal,
        tokens: BigDecimal,
    ): Result<BigDecimal> {
        return runCatching {
            val abOverC = a.multiply(b, mc).divide(c, mc)
            val cvPlusAbOverC = currentSupply.add(abOverC, mc)
            val cTimesTokensToSell = c.multiply(tokens, mc)
            val negCTimesTokensToSell = cTimesTokensToSell.negate(mc)
            val exp = negCTimesTokensToSell.exp(mc)
            val oneMinsExp = BigDecimal.ONE.subtract(exp, mc)
            cvPlusAbOverC.multiply(oneMinsExp, mc)
        }
    }

    /**
     * Calculate the number of tokens that can be bought for a given value, starting from the current supply.
     * This function answers the question: "How many tokens can I get for Y value?"
     * The formula used is: `num_tokens = (1/c) * ln(value / (a * b / c) + e^(c * current_supply)) - current_supply`
     *
     * @param currentSupply The current supply of tokens.
     * @param value The amount of value (e.g., currency) to be spent on tokens.
     * @return A [Result] containing the calculated number of tokens that can be bought, or an exception if an arithmetic error occurs.
     */
    override fun valueToTokens(
        currentSupply: BigDecimal,
        value: BigDecimal,
    ): Result<BigDecimal> {
        return runCatching {
            val abOverC = a.multiply(b, mc).divide(c, mc)
            val expCs = currentSupply.multiply(c, mc).exp(mc)
            val term = value.divide(abOverC, mc).add(expCs, mc)
            val lnTerm = term.ln(mc)
            val result = lnTerm.divide(c, mc)
            result.subtract(currentSupply, mc)
        }
    }

    /**
     * Calculate the number of tokens that can be exchanged for a given value, starting from the current supply.
     * This is the inverse of `valueFromSellingTokens`. It answers the question: "How many tokens should be exchanged for a value given the currentValue?"
     * @param currentValue The current supply of tokens.
     * @param value The target value to receive from the exchange.
     * @return A [Result] containing the calculated number of tokens for the exchange, or an exception if an arithmetic error occurs (e.g., input to log is not positive).
     */
    override fun tokensForValueExchange(
        currentValue: BigDecimal,
        value: BigDecimal,
    ): Result<Valuation.Tokens> {
        return runCatching {
            val abOverC = a.multiply(b, mc).divide(c, mc)
            val newValue = currentValue - value
            val currentValueOverAbOverC = currentValue.divide(abOverC, mc)
            val newValueOverAbOverC = newValue.divide(abOverC, mc)

            val lnTerm1 = (BigDecimal.ONE.add(currentValueOverAbOverC)).ln(mc)
            val lnTerm2 = (BigDecimal.ONE.add(newValueOverAbOverC)).ln(mc)
            val diffLnTerms = lnTerm1.subtract(lnTerm2, mc)

            val tokens = diffLnTerms.divide(c, mc)
            val fx = value.divide(tokens, mc)
            Valuation.Tokens(tokens, fx)
        }
    }

    override fun supplyFromValue(value: BigDecimal): Result<BigDecimal> = runCatching {
        require(value.signum() >= 0) { "Value must be non-negative" }

        if (value == BigDecimal.ZERO) return@runCatching BigDecimal.ZERO

        val abOverC = a.multiply(b, mc).divide(c, mc)
        val term = value.divide(abOverC, mc).add(BigDecimal.ONE, mc)
        val lnTerm = term.ln(mc)
        lnTerm.divide(c, mc)
    }

    override fun formattedTable(): String {
        return buildString {
            appendLine("|------|----------------|----------------------------------|----------------------------|")
            appendLine("| %    | S              | R(S)                             | R'(S)                      |")
            appendLine("|------|----------------|----------------------------------|----------------------------|")

            val zero = BigDecimal.ZERO
            val buyAmount = BigDecimal("210000") // Adjusted for unscaled
            var supply = zero

            for (i in 0..100) {
                val cost = costToBuyTokens(zero, supply).getOrThrow()
                val spotPrice = spotPriceAtSupply(supply).getOrThrow()
                appendLine(
                    "| %3d%% | %14s | %32s | %26s |".format(
                        i,
                        supply.toBigInteger().toString(),
                        formatter.format(cost),
                        formatter.format(spotPrice)
                    )
                )

                supply = supply.add(buyAmount)
            }

            appendLine("|------|----------------|----------------------------------|----------------------------|")
        }
    }
}