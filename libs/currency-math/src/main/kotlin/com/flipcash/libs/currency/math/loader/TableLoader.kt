package com.flipcash.libs.currency.math.loader

import java.math.BigDecimal
import java.math.BigInteger

interface TableLoader {
    fun loadTable(name: String): List<BigDecimal>

    fun bigDecimalFromParts(low: Long, high: Long): BigDecimal {
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