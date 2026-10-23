package com.getcode.opencode.internal.extensions

import com.getcode.opencode.model.financial.CurrencyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyCodeExtensionsTest {

    @Test
    fun afnHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.AFN.fractionDigits)
    }

    @Test
    fun copHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.COP.fractionDigits)
    }

    @Test
    fun idrHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.IDR.fractionDigits)
    }

    @Test
    fun irrHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.IRR.fractionDigits)
    }

    @Test
    fun mgaHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.MGA.fractionDigits)
    }

    @Test
    fun mruHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.MRU.fractionDigits)
    }

    @Test
    fun tzsHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.TZS.fractionDigits)
    }

    @Test
    fun uyuHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.UYU.fractionDigits)
    }

    @Test
    fun usdHasTwoFractionDigits() {
        assertEquals(2, CurrencyCode.USD.fractionDigits)
    }

    @Test
    fun eurHasTwoFractionDigits() {
        assertEquals(2, CurrencyCode.EUR.fractionDigits)
    }

    @Test
    fun jpyHasZeroFractionDigits() {
        assertEquals(0, CurrencyCode.JPY.fractionDigits)
    }

    @Test
    fun kwdHasThreeFractionDigits() {
        assertEquals(3, CurrencyCode.KWD.fractionDigits)
    }

    @Test
    fun gbpHasTwoFractionDigits() {
        assertEquals(2, CurrencyCode.GBP.fractionDigits)
    }
}
