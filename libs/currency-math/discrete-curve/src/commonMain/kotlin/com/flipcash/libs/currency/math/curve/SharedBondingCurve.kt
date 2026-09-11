package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.decimal.BigDecimal

/** tokens, fx as decimal strings -- the Kotlin/Native-exported counterpart of [CurveExchangeResult]. */
data class SharedCurveExchangeResult(val tokens: String, val fx: String)

/**
 * Discrete bonding-curve pricing engine, exposed through the `SharedCore` XCFramework. All
 * amounts cross the Kotlin/Native boundary as decimal strings (matching the module's other
 * facades' avoidance of Kotlin-library-specific types at the boundary) -- callers convert to/from
 * their platform's own decimal type (`java.math.BigDecimal` on Android, `BigDecimal`/`Decimal` on
 * iOS) immediately on either side.
 */
object SharedBondingCurve {
    private const val MAX_SUPPLY = 21_000_000

    fun initialize(pricingTableBytes: ByteArray, cumulativeTableBytes: ByteArray) {
        DiscreteCurveEngine.initialize(pricingTableBytes, cumulativeTableBytes)
    }

    /** Returns null if `supply` is out of range. */
    fun spotPriceAtSupply(supply: Int): String? =
        DiscreteCurveEngine.spotPriceAtSupply(supply, MAX_SUPPLY)?.toStringExpanded()

    /** Returns null if tokens is negative or the resulting supply exceeds the table. */
    fun tokensToValue(currentSupply: String, tokens: String): String? =
        DiscreteCurveEngine.tokensToValue(BigDecimal.parseString(currentSupply), BigDecimal.parseString(tokens))
            ?.toStringExpanded()

    /** Returns null if at/beyond max supply. */
    fun valueToTokens(currentSupply: Int, value: String): String? =
        DiscreteCurveEngine.valueToTokens(currentSupply, BigDecimal.parseString(value))?.toStringExpanded()

    /** Returns null if the exchange is invalid (value <= 0, value > currentValue, or resulting tokens <= 0). */
    fun tokensForValueExchange(currentValue: String, value: String): SharedCurveExchangeResult? {
        val result = DiscreteCurveEngine.tokensForValueExchange(BigDecimal.parseString(currentValue), BigDecimal.parseString(value))
            ?: return null
        return SharedCurveExchangeResult(tokens = result.tokens.toStringExpanded(), fx = result.fx.toStringExpanded())
    }

    fun preciseSupplyFromValue(value: String): String =
        DiscreteCurveEngine.preciseSupplyFromValue(BigDecimal.parseString(value)).toStringExpanded()

    // Deviation from the plan's literal two-param `supplyFromTVL(tvlQuarks, quarksPerToken): Long?` --
    // Task 5 re-derived this from iOS's actual `supplyFromTVL(_ tvlQuarks: Int)` body, which divides
    // by a fixed USDC-quarks constant (1_000_000), not a caller-supplied `quarksPerToken`. There is no
    // "invalid input" case in the ported engine (it always returns a step index, snapping negative/huge
    // inputs to the nearest valid step), so the return type stays non-nullable to match
    // `DiscreteCurveEngine.supplyFromTVL(tvlQuarks: Long): Long`.
    fun supplyFromTVL(tvlQuarks: Long): Long =
        DiscreteCurveEngine.supplyFromTVL(tvlQuarks)

    fun formattedTable(): String = DiscreteCurveEngine.formattedTable()

    // Raw scaled-integer (18-decimal fixed point) table access -- exposed only for Android's
    // pre-existing table-validation regression tests (`DiscreteBondingCurveTests.kt`), which predate
    // this port and directly inspected the old `LazyBigDecimalTable`-backed representation. Not part
    // of the pricing API proper; every real call site uses the human-scale methods above instead.
    fun pricingTableSize(): Int = DiscreteCurveTables.pricingTable.size
    fun cumulativeTableSize(): Int = DiscreteCurveTables.cumulativeTable.size
    fun pricingTableRawAt(index: Int): String = DiscreteCurveTables.pricingTable[index].toString()
    fun cumulativeTableRawAt(index: Int): String = DiscreteCurveTables.cumulativeTable[index].toString()
}
