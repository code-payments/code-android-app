package com.flipcash.libs.currency.math

import android.content.Context
import com.flipcash.libs.currency.math.internal.DefaultMintMaxTokenSupply
import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import com.flipcash.libs.currency.math.internal.loader.AndroidTableLoader
import com.flipcash.libs.currency.math.loader.TableLoader
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

object Curves {
    suspend fun initialize(context: Context) {
        // only discrete needs to be initialized
        val tableLoader = AndroidTableLoader(context)
        DiscreteBondingCurve.initialize(tableLoader)
    }
}