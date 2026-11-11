package com.flipcash.libs.currency.math.decimal

actual typealias BigDecimal = java.math.BigDecimal

actual typealias MathContext = java.math.MathContext

actual typealias RoundingMode = java.math.RoundingMode

actual object Decimals {
    actual val ZERO: BigDecimal = java.math.BigDecimal.ZERO
    actual val ONE: BigDecimal = java.math.BigDecimal.ONE
    actual val TEN: BigDecimal = java.math.BigDecimal.TEN

    actual fun valueOf(value: Long): BigDecimal = java.math.BigDecimal.valueOf(value)

    actual fun valueOf(value: Double): BigDecimal = java.math.BigDecimal.valueOf(value)
}
