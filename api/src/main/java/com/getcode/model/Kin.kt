package com.getcode.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor

data class Kin(val quarks: Long): Value {
    init {
        if (quarks < 0) {
            throw IllegalStateException()
        }
    }

    fun toKin(): BigDecimal =
        BigDecimal(quarks).divide(BigDecimal(QUARK_CONVERSION_RATE))

    fun toKinValueDouble() = toKin().toDouble()
    fun toKinTruncatingLong() = floor(toKinValueDouble()).toLong()

    fun toKinTruncating() = fromKin(toKinTruncatingLong())
    fun fractionalQuarks() = fromQuarks(quarks - toKinTruncating().quarks)
    fun inflating(): Kin = if (fractionalQuarks() > 0) fromKin(toKinTruncatingLong() + 1) else this
    fun toFiat(fx: Double) = toKinValueDouble() * fx
    fun hasWholeKin() = toKinTruncatingLong() > 0
    operator fun plus(other: Kin): Kin = Kin(this.quarks + other.quarks)
    operator fun minus(other: Kin): Kin = Kin(this.quarks - other.quarks)
    operator fun div(other: Kin): Kin = Kin(this.quarks / other.quarks)
    operator fun div(other: Int): Kin = this / fromQuarks(other.toLong())
    operator fun times(other: Kin): Kin = Kin(this.quarks * other.quarks)
    operator fun times(other: Int): Kin = Kin(this.quarks * other)

    operator fun compareTo(int: Int): Int = quarks.compareTo(fromKin(int).quarks)
    operator fun compareTo(kin: Kin): Int = quarks.compareTo(kin.quarks)


    companion object {
        private const val QUARK_CONVERSION_RATE = 100_000
        fun fromKin(kin: Int) = fromKin(kin.toDouble())
        fun fromKin(kin: Long) = fromKin(kin.toDouble())
        fun fromKin(kin: Double): Kin {
            return Kin(
                BigDecimal(kin)
                    .setScale(5, RoundingMode.UP)
                    .multiply(BigDecimal(QUARK_CONVERSION_RATE))
                    .toLong()
            )
        }

        fun fromQuarks(quarks: Long) = Kin(quarks)
        fun fromFiat(fiat: Double, fx: Double) = fromKin(fiat / fx)
    }
}