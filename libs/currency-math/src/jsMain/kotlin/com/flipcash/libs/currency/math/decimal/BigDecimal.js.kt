package com.flipcash.libs.currency.math.decimal

actual typealias BigDecimal = CommonBigDecimal

/** `java.math.MathContext`. */
actual class MathContext actual constructor(
    private val setPrecision: Int,
    private val setRoundingMode: RoundingMode,
) {
    init {
        require(setPrecision >= 0) { "Digits < 0" }
    }

    actual fun getPrecision(): Int = setPrecision
    actual fun getRoundingMode(): RoundingMode = setRoundingMode

    override fun equals(other: Any?): Boolean =
        other is MathContext && setPrecision == other.setPrecision && setRoundingMode == other.setRoundingMode

    override fun hashCode(): Int = setRoundingMode.hashCode() + (setPrecision shl 2)

    override fun toString(): String = "precision=$setPrecision roundingMode=$setRoundingMode"
}

/** `java.math.RoundingMode`. */
actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY,
}

actual object Decimals {
    actual val ZERO: BigDecimal = CommonBigDecimal.ZERO
    actual val ONE: BigDecimal = CommonBigDecimal.ONE
    actual val TEN: BigDecimal = CommonBigDecimal.TEN

    actual fun valueOf(value: Long): BigDecimal = CommonBigDecimal.valueOf(value)

    // Kotlin/JS's Double.toString is Number.prototype.toString: the same shortest digits as Java's.
    actual fun valueOf(value: Double): BigDecimal = CommonBigDecimal.valueOf(value, value.toString())
}
