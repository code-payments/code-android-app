package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintMaxTokenSupply
import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import com.flipcash.libs.currency.math.internal.loader.FileTableLoader
import com.flipcash.libs.currency.math.internal.loader.TableByteLoader
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat

@org.jetbrains.annotations.VisibleForTesting
internal val mc: MathContext = MathContext(50, RoundingMode.HALF_EVEN)

internal const val PRECISION = 18

internal val formatter: DecimalFormat by lazy {
    val pattern = "0." + "0".repeat(PRECISION)
    DecimalFormat(pattern).apply {
        // Optional: configure additional behavior
        roundingMode = RoundingMode.HALF_UP
        isGroupingUsed = false
    }
}
internal val START_PRICE = BigDecimal("0.01")
internal val END_PRICE = BigDecimal("1000000")
internal val MAX_SUPPLY = BigDecimal("$DefaultMintMaxTokenSupply")

enum class CurveType {
    Continuous,
    Discrete,
    ;
}

interface BondingCurve {
    fun spotPriceAtSupply(supply: BigDecimal): Result<BigDecimal>
    fun tokensToValue(currentSupply: BigDecimal, tokens: BigDecimal): Result<BigDecimal>
    fun valueToTokens(currentSupply: BigDecimal, value: BigDecimal): Result<BigDecimal>
    fun tokensForValueExchange(currentValue: BigDecimal, value: BigDecimal): Result<Valuation.Tokens>
    fun formattedTable(): String
}

/**
 * Loads the discrete curve's tables. Each platform adds its own `initialize` overload that picks the
 * table source: the APK's assets on Android, the classpath on the desktop JVM.
 */
object Curves {
    internal suspend fun initialize(tableLoader: TableByteLoader) {
        // only discrete needs to be initialized
        DiscreteBondingCurve.initialize(tableLoader)
    }

    /** For host tests: reads the tables from this repo's source tree, found by walking up from the working directory. */
    suspend fun initializeFromSourceTree() = initialize(FileTableLoader())
}