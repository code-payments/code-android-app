package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

/** tokens, fx -- mirrors Android's `Valuation.Tokens` shape without depending on that type here. */
internal data class CurveExchangeResult(val tokens: BigDecimal, val fx: BigDecimal)

/**
 * Discrete bonding-curve pricing engine: table-driven step pricing plus the two binary searches
 * that invert it. Ported from `code-android-app`'s `internal/curves/DiscreteBondingCurve.kt` and
 * `code-ios-app`'s `DiscreteBondingCurve.swift`'s low-level methods -- see the plan's module doc
 * for the two behavioral fixes and the raw-integer binary-search domain this version uses.
 */
internal object DiscreteCurveEngine {
    const val STEP_SIZE = 100
    const val TABLE_SIZE = 210_001

    // iOS's `supplyFromTVL(_ tvlQuarks: Int)` divides by a fixed 1_000_000 -- USDC's own quark
    // scale (6 decimals), not the bonding-curve token's `quarksPerToken` (10 decimals). `tvlQuarks`
    // here is USDC-quarks of total-value-locked, independent of the curve token's own decimals.
    private const val USDC_QUARKS_PER_UNIT = 1_000_000L

    fun initialize(pricingTableBytes: ByteArray, cumulativeTableBytes: ByteArray) {
        DiscreteCurveTables.initialize(pricingTableBytes, cumulativeTableBytes)
    }

    fun spotPriceAtSupply(supply: Int, maxSupply: Int): BigDecimal? {
        if (supply < 0 || supply > maxSupply) return null
        val stepIndex = supply / STEP_SIZE
        val table = DiscreteCurveTables.pricingTable
        if (stepIndex >= table.size) return null
        return fromScaledBigInteger(table[stepIndex])
    }

    fun tokensToValue(currentSupply: BigDecimal, tokens: BigDecimal): BigDecimal? {
        if (tokens.isNegative) return null
        if (tokens.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        val pricingTable = DiscreteCurveTables.pricingTable
        val cumulativeTable = DiscreteCurveTables.cumulativeTable
        val stepSizeDecimal = BigDecimal.fromInt(STEP_SIZE)

        val endSupply = currentSupply.addHP(tokens)
        val startStep = currentSupply.divide(stepSizeDecimal, curveDecimalMode).toStringExpanded().substringBefore('.').toInt()
        val endStep = endSupply.divide(stepSizeDecimal, curveDecimalMode).toStringExpanded().substringBefore('.').toInt()

        if (endStep >= pricingTable.size) return null // cannot sell more tokens than current supply

        val startStepBoundary = BigDecimal.fromInt((startStep + 1) * STEP_SIZE)
        val tokensInStartStep = if (startStepBoundary.compareTo(endSupply) > 0) {
            tokens
        } else {
            startStepBoundary.subtractHP(currentSupply)
        }

        val startPrice = fromScaledBigInteger(pricingTable[startStep])
        val startCost = tokensInStartStep.multiplyHP(startPrice)

        if (startStep == endStep) return startCost

        val cumulativeStart = fromScaledBigInteger(cumulativeTable[startStep + 1])
        val cumulativeEnd = fromScaledBigInteger(cumulativeTable[endStep])
        val middleCost = cumulativeEnd.subtractHP(cumulativeStart)

        val endStepBoundary = BigDecimal.fromInt(endStep * STEP_SIZE)
        val tokensInEndStep = endSupply.subtractHP(endStepBoundary)

        val endPrice = fromScaledBigInteger(pricingTable[endStep])
        val endCost = tokensInEndStep.multiplyHP(endPrice)

        return startCost.addHP(middleCost).addHP(endCost)
    }

    fun valueToTokens(currentSupply: Int, value: BigDecimal): BigDecimal? {
        if (value.isNegative || currentSupply < 0) return null
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        val pricingTable = DiscreteCurveTables.pricingTable
        val cumulativeTable = DiscreteCurveTables.cumulativeTable

        val startStep = currentSupply / STEP_SIZE
        if (startStep >= pricingTable.size - 1) return null // at max supply

        val startStepBoundary = (startStep + 1) * STEP_SIZE
        val tokensToCompleteStartStep = startStepBoundary - currentSupply
        val startPrice = fromScaledBigInteger(pricingTable[startStep])
        val costToCompleteStartStep = BigDecimal.fromInt(tokensToCompleteStartStep).multiplyHP(startPrice)

        if (value.compareTo(costToCompleteStartStep) < 0) {
            return value.divideHP(startPrice)
        }

        val remainingAfterStart = value.subtractHP(costToCompleteStartStep)
        val baseCumulative = fromScaledBigInteger(cumulativeTable[startStep + 1])
        val targetCumulative = baseCumulative.addHP(remainingAfterStart)
        val targetCumulativeScaled = toScaledBigInteger(targetCumulative)

        val endStep = binarySearchRaw(
            from = startStep + 1,
            to = cumulativeTable.size - 1,
            target = targetCumulativeScaled,
            value = { cumulativeTable[it] },
        )
        if (endStep >= pricingTable.size) return null // exceeds max supply

        val endStepSupply = endStep * STEP_SIZE
        val tokensFromCompleteSteps = endStepSupply - startStepBoundary

        val cumulativeAtEndStep = fromScaledBigInteger(cumulativeTable[endStep])
        val valueUsedForCompleteSteps = cumulativeAtEndStep.subtractHP(baseCumulative)
        val remainingValue = remainingAfterStart.subtractHP(valueUsedForCompleteSteps)

        val endPrice = fromScaledBigInteger(pricingTable[endStep])
        val tokensInEndStep = remainingValue.divideHP(endPrice)

        return BigDecimal.fromInt(tokensToCompleteStartStep)
            .addHP(BigDecimal.fromInt(tokensFromCompleteSteps))
            .addHP(tokensInEndStep)
    }

    fun tokensForValueExchange(currentValue: BigDecimal, value: BigDecimal): CurveExchangeResult? {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return null // value must be positive
        if (currentValue.isNegative) return null
        if (value.compareTo(currentValue) > 0) return null // exchange value cannot exceed current value

        val newValue = currentValue.subtractHP(value)
        val currentSupply = preciseSupplyFromValue(currentValue)
        val newSupply = preciseSupplyFromValue(newValue)

        val tokens = currentSupply.subtractHP(newSupply)
        if (tokens.compareTo(BigDecimal.ZERO) <= 0) return null // calculated tokens must be positive

        val fx = value.divideHP(tokens)
        return CurveExchangeResult(tokens = tokens, fx = fx)
    }

    /**
     * Precise supply at a given TVL, interpolated within the step (fractional tokens). Unlike
     * [supplyFromTVL], which snaps to step boundaries, this is the exact inverse of a TVL value.
     */
    fun preciseSupplyFromValue(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO

        val cumulativeTable = DiscreteCurveTables.cumulativeTable
        val pricingTable = DiscreteCurveTables.pricingTable
        val valueScaled = toScaledBigInteger(value)

        val stepIndex = binarySearchRaw(
            from = 0,
            to = cumulativeTable.size - 1,
            target = valueScaled,
            value = { cumulativeTable[it] },
        )
        val stepSupply = stepIndex * STEP_SIZE

        val cumulativeAtStep = fromScaledBigInteger(cumulativeTable[stepIndex])
        val remainingValue = value.subtractHP(cumulativeAtStep)
        if (remainingValue.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.fromInt(stepSupply)

        val priceAtStep = fromScaledBigInteger(pricingTable[stepIndex])
        if (priceAtStep.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.fromInt(stepSupply)

        val fractionalTokens = remainingValue.divideHP(priceAtStep)
        val stepSizeDecimal = BigDecimal.fromInt(STEP_SIZE)
        val cappedFractional = if (fractionalTokens.compareTo(stepSizeDecimal) < 0) fractionalTokens else stepSizeDecimal

        return BigDecimal.fromInt(stepSupply).addHP(cappedFractional)
    }

    /**
     * Supply at a given TVL, snapped to the step boundary (whole-token supply, not interpolated).
     * `tvlQuarks` is USDC-quarks (USDC's own 6-decimal smallest unit) -- re-derived from iOS's
     * current `supplyFromTVL(_ tvlQuarks: Int)` body, which divides by a fixed `1_000_000` rather
     * than the bonding-curve token's `quarksPerToken` (10 decimals); the two are unrelated units.
     */
    fun supplyFromTVL(tvlQuarks: Long): Long {
        val cumulativeTable = DiscreteCurveTables.cumulativeTable
        val tvl = BigDecimal.fromLong(tvlQuarks).divideHP(BigDecimal.fromLong(USDC_QUARKS_PER_UNIT))
        val tvlScaled = toScaledBigInteger(tvl)

        val stepIndex = binarySearchRaw(
            from = 0,
            to = cumulativeTable.size - 1,
            target = tvlScaled,
            value = { cumulativeTable[it] },
        )
        return (stepIndex * STEP_SIZE).toLong()
    }

    fun formattedTable(): String = buildString {
        val pricingTable = DiscreteCurveTables.pricingTable
        appendLine("Discrete Bonding Curve Table (first 20 entries):")
        appendLine("Step | Supply Range | Price (USDF)")
        appendLine("-".repeat(50))
        pricingTable.take(20).forEachIndexed { index, priceRaw ->
            val supplyStart = index * STEP_SIZE
            val supplyEnd = supplyStart + STEP_SIZE - 1
            appendLine("$index | $supplyStart-$supplyEnd | ${fromScaledBigInteger(priceRaw)}")
        }
        if (pricingTable.size > 20) {
            appendLine("... (${pricingTable.size - 20} more entries)")
        }
    }

    /** Finds the largest index in [from, to] where value(index) <= target. Returns `from` if none. */
    private fun binarySearchRaw(from: Int, to: Int, target: BigInteger, value: (Int) -> BigInteger): Int {
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
