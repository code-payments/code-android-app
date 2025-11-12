package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.DefaultMintQuarksPerUnit
import java.math.BigDecimal

fun BigDecimal.units(): BigDecimal = this.divide(BigDecimal(DefaultMintQuarksPerUnit), mc)
fun BigDecimal.divideWithHighPrecision(other: BigDecimal): BigDecimal = this.divide(other, mc)
fun BigDecimal.multiplyWithHighPrecision(other: BigDecimal): BigDecimal = this.multiply(other, mc)