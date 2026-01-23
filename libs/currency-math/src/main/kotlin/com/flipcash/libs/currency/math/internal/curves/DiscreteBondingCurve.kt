package com.flipcash.libs.currency.math.internal.curves

import com.flipcash.libs.currency.math.BondingCurve
import com.flipcash.libs.currency.math.MAX_SUPPLY
import com.flipcash.libs.currency.math.PRECISION
import com.flipcash.libs.currency.math.Valuation
import com.flipcash.libs.currency.math.addWithHighPrecision
import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.internal.DefaultMintDecimals
import com.flipcash.libs.currency.math.internal.DefaultMintQuarksPerUnit
import com.flipcash.libs.currency.math.loader.Table
import com.flipcash.libs.currency.math.loader.TableLoader
import com.flipcash.libs.currency.math.mc
import com.flipcash.libs.currency.math.multiplyWithHighPrecision
import com.flipcash.libs.currency.math.subtractWithHighPrecision
import com.flipcash.libs.currency.math.units
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.VisibleForTesting
import java.math.BigDecimal
import java.math.RoundingMode

internal class DiscreteBondingCurve private constructor(
    @VisibleForTesting
    internal val pricingTable: Table,
    @VisibleForTesting
    internal val cumulativeTable: Table,
) : BondingCurve {

    companion object {
        @Volatile
        private var instance: DiscreteBondingCurve? = null

        suspend fun initialize(tableLoader: TableLoader) {
            if (instance == null) {
                val (pricingTable, cumulativeTable) = coroutineScope {
                    val pricing = async { tableLoader.loadTable("discrete_pricing_table") }
                    val cumulative = async { tableLoader.loadTable("discrete_cumulative_table") }
                    pricing.await() to cumulative.await()
                }

                synchronized(this) {
                    if (instance == null) {
                        instance = DiscreteBondingCurve(pricingTable, cumulativeTable)
                    }
                }
            }
        }

        fun getOrThrow(): DiscreteBondingCurve {
            return instance
                ?: throw IllegalStateException("DiscreteBondingCurve not initialized")
        }

        // Step size in tokens (100 tokens per step)
        internal const val stepSize = 100

        // Number of decimal places for table values (18, matching Rust)
        internal const val tablePrecision = PRECISION

        // Token decimals (10 decimal places)
        private const val decimals = 10

        // Maximum token supply
        internal val maxSupply = MAX_SUPPLY.toInt()

        // Number of steps in the tables (210,001 entries: 0 to 21,000,000 in steps of 100)
        internal const val tableSize = 210_001

        // Quarks per whole token (10^10 for 10 decimal places)
        internal const val quarksPerToken = DefaultMintQuarksPerUnit

        internal val scaleFactor = BigDecimal.TEN.pow(tablePrecision)
    }


    override fun spotPriceAtSupply(supply: BigDecimal): Result<BigDecimal> = runCatching {
        val supply = supply.setScale(0, RoundingMode.DOWN).toInt()
        require(supply >= 0) { "Supply must be non-negative" }
        require(supply <= maxSupply) { "Supply exceeds max supply" }

        val stepIndex = supply / stepSize
        require(stepIndex < pricingTable.size) { "Step index out of bounds" }

        pricingTable[stepIndex]
    }

    override fun tokensToValue(
        currentSupply: BigDecimal,
        tokens: BigDecimal
    ): Result<BigDecimal> = runCatching {
        require(tokens.signum() >= 0) { "Tokens to sell must be non-negative" }

        if (tokens == BigDecimal.ZERO) return@runCatching BigDecimal.ZERO

        val endSupply = currentSupply  + tokens
        val startStep = currentSupply.divideToIntegralValue(stepSize.toBigDecimal())
        val endStep = endSupply.divideToIntegralValue(stepSize.toBigDecimal())

        require(endStep.toInt() < pricingTable.size) { "Cannot sell more tokens than current supply" }

        // Calculate partial tokens in start step (from currentSupply to next step boundary)
        val startStepBoundary = (startStep.add(BigDecimal.ONE)).multiply(stepSize.toBigDecimal())
        val tokensInStartStep = if (startStepBoundary > endSupply) {
            // All tokens are within the same step
            tokens
        } else {
            startStepBoundary - currentSupply
        }

        // Cost for partial start step
        val startPrice = pricingTable[startStep.toInt()]
        val startCost = tokensInStartStep.multiplyWithHighPrecision(startPrice)

        if (startStep == endStep) {
            return@runCatching startCost
        }

        // Cost for complete steps between start_step+1 and end_step-1 (inclusive)
        // Use cumulative table: cumulative[end_step] - cumulative[start_step + 1]
        val cumulativeStart = cumulativeTable[startStep.toInt() + 1]
        val cumulativeEnd = cumulativeTable[endStep.toInt()]
        val middleCost = cumulativeEnd.subtractWithHighPrecision(cumulativeStart)

        // Calculate partial tokens in end step (from end step boundary to end_supply)
        val endStepBoundary = endStep.multiply(stepSize.toBigDecimal())
        val tokensInEndStep = endSupply - endStepBoundary

        // Cost for partial end step
        val endPrice = pricingTable[endStep.toInt()]
        val endCost = tokensInEndStep.multiplyWithHighPrecision(endPrice)

        startCost.addWithHighPrecision(middleCost).addWithHighPrecision(endCost)
    }

    override fun valueToTokens(
        currentSupply: BigDecimal,
        value: BigDecimal
    ): Result<BigDecimal> = runCatching {
        val supply = currentSupply.setScale(0, RoundingMode.DOWN).toInt()

        require(supply >= 0) { "Current supply must be non-negative" }
        require(value >= BigDecimal.ZERO) { "Value must be non-negative" }

        if (value == BigDecimal.ZERO) return@runCatching BigDecimal.ZERO

        val startStep = supply / stepSize
        require(startStep < pricingTable.size - 1) { "At max supply" }

        // Calculate cost to complete the current partial step
        val startStepBoundary = (startStep + 1) * stepSize
        val tokensToCompleteStartStep = startStepBoundary - supply
        val startPrice = pricingTable[startStep]
        val costToCompleteStartStep = BigDecimal(tokensToCompleteStartStep).multiplyWithHighPrecision(startPrice)

        // If we can't even complete the start step, just divide by price
        if (value < costToCompleteStartStep) {
            return@runCatching value.divideWithHighPrecision(startPrice)
        }

        // We can at least complete the start step
        val remainingAfterStart = value.subtractWithHighPrecision(costToCompleteStartStep)

        // Calculate the cumulative value at start_step + 1
        val baseCumulative = cumulativeTable[startStep + 1]

        // Target cumulative = base_cumulative + remaining_value
        val targetCumulative = baseCumulative.addWithHighPrecision(remainingAfterStart)

        // Binary search for the step where cumulative value exceeds or equals target
        val endStep = binarySearch(
            from = startStep + 1,
            to = cumulativeTable.lastIndex,
            value = { cumulativeTable[it] },
            target = targetCumulative,
        )
        require(endStep < pricingTable.size) { "Exceeds max supply" }

        // Calculate tokens from complete steps
        val endStepSupply = endStep * stepSize
        val tokensFromCompleteSteps = endStepSupply - startStepBoundary

        // Calculate remaining value after complete steps
        val cumulativeAtEndStep = cumulativeTable[endStep]
        val valueUsedForCompleteSteps = cumulativeAtEndStep.subtractWithHighPrecision(baseCumulative)
        val remainingValue = remainingAfterStart.subtractWithHighPrecision(valueUsedForCompleteSteps)

        // Buy partial tokens in end step with remaining value
        val endPrice = pricingTable[endStep]
        val tokensInEndStep = remainingValue.divideWithHighPrecision(endPrice)

        BigDecimal(tokensToCompleteStartStep)
            .addWithHighPrecision(BigDecimal(tokensFromCompleteSteps))
            .addWithHighPrecision(tokensInEndStep)
    }

    override fun tokensForValueExchange(
        currentValue: BigDecimal,
        value: BigDecimal
    ): Result<Valuation.Tokens> = runCatching {
        require(value.signum() > 0) { "Value must be positive" }
        require(currentValue.signum() >= 0) { "Current value must be non-negative" }
        require(value <= currentValue) { "Exchange value cannot exceed current value" }

        val newValue = currentValue.subtract(value)

        // Get precise supply at current TVL (interpolated within step)
        val currentSupply = preciseSupplyFromValue(currentValue)

        // Get precise supply at new (lower) TVL
        val newSupply = preciseSupplyFromValue(newValue)

        // Tokens = difference in supply
        val tokens = currentSupply.subtractWithHighPrecision(newSupply)
        require(tokens.signum() > 0) { "Calculated tokens must be positive" }

        val fx = value.divideWithHighPrecision(tokens)

        Valuation.Tokens(
            tokens = tokens,
            fx = fx,
        )
    }

    override fun formattedTable(): String {
        return buildString {
            appendLine("Discrete Bonding Curve Table (first 20 entries):")
            appendLine("Step | Supply Range | Price (USDF)")
            appendLine("-".repeat(50))
            pricingTable.take(20).forEachIndexed { index, price ->
                val supplyStart = index * stepSize
                val supplyEnd = supplyStart + stepSize - 1
                appendLine("$index | $supplyStart-$supplyEnd | $price")
            }
            if (pricingTable.size > 20) {
                appendLine("... (${pricingTable.size - 20} more entries)")
            }
        }
    }

    /**
     * Calculate precise supply from TVL with interpolation within steps.
     *
     * Unlike `supplyFromTVL` which returns step boundaries, this method
     * interpolates within the step to give a more accurate supply value.
     *
     * @param tvl Total value locked in USDF
     * @return Precise supply with fractional tokens
     */
    private fun preciseSupplyFromValue(value: BigDecimal): BigDecimal {
        if (value.signum() <= 0) {
            return BigDecimal.ZERO
        }

        val stepIndex = binarySearch(
            from = 0,
            to = cumulativeTable.lastIndex,
            value = { cumulativeTable[it] },
            target = value
        )
        val stepSupply = stepIndex * stepSize

        val cumulativeAtStep = cumulativeTable[stepIndex]
        val remainingValue = value.subtractWithHighPrecision(cumulativeAtStep)

        if (remainingValue.signum() <= 0) {
            return BigDecimal(stepSupply)
        }

        val priceAtStep = pricingTable[stepIndex]

        if (priceAtStep.signum() <= 0) {
            return BigDecimal(stepSupply)
        }

        val fractionalTokens = remainingValue.divideWithHighPrecision(priceAtStep)

        val stepSizeDecimal = BigDecimal(stepSize)
        val cappedFractional = if (fractionalTokens < stepSizeDecimal) {
            fractionalTokens
        } else {
            stepSizeDecimal
        }

        return BigDecimal(stepSupply).addWithHighPrecision(cappedFractional)
    }

    /**
     * Finds the largest index in [from, to] where [value] returns <= [target].
     * Returns [from] - 1 if no such index exists.
     */
    private fun binarySearch(
        from: Int,
        to: Int,
        target: BigDecimal,
        value: (Int) -> BigDecimal
    ): Int {
        var low = from
        var high = to

        while (low < high) {
            val mid = (low + high + 1) / 2
            if (value(mid) <= target) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        return low
    }
}