package com.flipcash.libs.currency.math.loader

import com.flipcash.libs.currency.math.divideWithHighPrecision
import com.flipcash.libs.currency.math.internal.curves.DiscreteBondingCurve
import java.math.BigDecimal
import java.math.BigInteger

typealias Table = LazyBigDecimalTable

interface TableLoader {
    suspend fun loadTable(name: String): Table
}

class LazyBigDecimalTable(
    private val lowBits: LongArray,
    private val highBits: LongArray,
    private val scaleFactor: BigDecimal = DiscreteBondingCurve.scaleFactor
) {
    val size: Int get() = lowBits.size
    val lastIndex: Int get() = size - 1

    val cache = arrayOfNulls<BigDecimal>(lowBits.size)

    fun getRaw(index: Int) = bigDecimalFromParts(lowBits[index], highBits[index])

    operator fun get(index: Int): BigDecimal {
        return cache[index] ?: run {
            val value = bigDecimalFromParts(lowBits[index], highBits[index]).divideWithHighPrecision(scaleFactor)
            cache[index] = value
            value
        }
    }

    fun take(count: Int): List<BigDecimal> {
        return (0 until count).map { get(it) }
    }

    private fun bigDecimalFromParts(low: Long, high: Long): BigDecimal {
        val highBig = if (high >= 0) {
            BigInteger.valueOf(high)
        } else {
            BigInteger.valueOf(high and Long.MAX_VALUE).add(BigInteger.ONE.shiftLeft(63))
        }

        val lowBig = if (low >= 0) {
            BigInteger.valueOf(low)
        } else {
            BigInteger.valueOf(low and Long.MAX_VALUE).add(BigInteger.ONE.shiftLeft(63))
        }

        return highBig.shiftLeft(64).add(lowBig).toBigDecimal()
    }
}