package com.flipcash.libs.currency.math.internal.curves

import com.flipcash.libs.currency.math.BondingCurve
import com.flipcash.libs.currency.math.Valuation
import com.flipcash.libs.currency.math.curve.SharedBondingCurve
import com.flipcash.libs.currency.math.internal.loader.TableByteLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Thin wrapper over a raw scaled-integer table's size and per-index raw value as [BigDecimal] --
 * backs [DiscreteBondingCurve.pricingTable]/[DiscreteBondingCurve.cumulativeTable], kept only so
 * the pre-existing table-validation regression tests (which predate this port and directly
 * inspected the old `LazyBigDecimalTable`-backed representation) keep compiling unchanged.
 */
internal class RawCurveTable(val size: Int, private val rawAt: (Int) -> String) {
    fun getRaw(index: Int): BigDecimal = BigDecimal(rawAt(index))
}

/**
 * Thin adapter delegating pricing to the shared KMP engine (`:libs:currency-math:discrete-curve`)
 * exported through `SharedBondingCurve`. All the actual table-lookup/binary-search/step-arithmetic
 * logic now lives in that module's `commonMain` -- this object only converts to/from
 * `java.math.BigDecimal` at the boundary and keeps the pre-existing public shape
 * ([BondingCurve], [initialize]/[getOrThrow], and the raw-table/constant members below).
 */
internal object DiscreteBondingCurve : BondingCurve {
    const val stepSize: Int = 100
    const val quarksPerToken: Long = 10_000_000_000L
    const val maxSupply: Int = 21_000_000
    const val tableSize: Int = 210_001
    const val tablePrecision: Int = 18

    val pricingTable: RawCurveTable
        get() = RawCurveTable(SharedBondingCurve.pricingTableSize()) { SharedBondingCurve.pricingTableRawAt(it) }

    val cumulativeTable: RawCurveTable
        get() = RawCurveTable(SharedBondingCurve.cumulativeTableSize()) { SharedBondingCurve.cumulativeTableRawAt(it) }

    @Volatile
    private var initialized = false

    suspend fun initialize(tableLoader: TableByteLoader) {
        if (initialized) return
        val (pricingBytes, cumulativeBytes) = coroutineScope {
            val pricing = async { tableLoader.loadTableBytes("discrete_pricing_table") }
            val cumulative = async { tableLoader.loadTableBytes("discrete_cumulative_table") }
            pricing.await() to cumulative.await()
        }
        synchronized(this) {
            if (!initialized) {
                SharedBondingCurve.initialize(pricingBytes, cumulativeBytes)
                initialized = true
            }
        }
    }

    fun getOrThrow(): DiscreteBondingCurve {
        check(initialized) { "DiscreteBondingCurve not initialized" }
        return this
    }

    override fun spotPriceAtSupply(supply: BigDecimal): Result<BigDecimal> = runCatching {
        val supplyInt = supply.setScale(0, RoundingMode.DOWN).toInt()
        val result = SharedBondingCurve.spotPriceAtSupply(supplyInt)
            ?: throw IllegalArgumentException("Supply out of range")
        BigDecimal(result)
    }

    override fun tokensToValue(currentSupply: BigDecimal, tokens: BigDecimal): Result<BigDecimal> = runCatching {
        val result = SharedBondingCurve.tokensToValue(currentSupply.toPlainString(), tokens.toPlainString())
            ?: throw IllegalArgumentException("Cannot sell more tokens than current supply")
        BigDecimal(result)
    }

    override fun valueToTokens(currentSupply: BigDecimal, value: BigDecimal): Result<BigDecimal> = runCatching {
        val supplyInt = currentSupply.setScale(0, RoundingMode.DOWN).toInt()
        val result = SharedBondingCurve.valueToTokens(supplyInt, value.toPlainString())
            ?: throw IllegalArgumentException("At max supply")
        BigDecimal(result)
    }

    override fun tokensForValueExchange(currentValue: BigDecimal, value: BigDecimal): Result<Valuation.Tokens> = runCatching {
        val result = SharedBondingCurve.tokensForValueExchange(currentValue.toPlainString(), value.toPlainString())
            ?: throw IllegalArgumentException("Invalid exchange")
        Valuation.Tokens(tokens = BigDecimal(result.tokens), fx = BigDecimal(result.fx))
    }

    override fun formattedTable(): String = SharedBondingCurve.formattedTable()
}
