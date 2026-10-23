package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FeeTest {

    @Test
    fun `Fee stores fiat amount and type`() {
        val amount = Fiat(fiat = 0.05)
        val fee = Fee(fiat = amount, type = FeeType.CreateOnSendWithdrawal)

        assertEquals(amount, fee.fiat)
        assertEquals(FeeType.CreateOnSendWithdrawal, fee.type)
    }

    @Test
    fun `Fee with zero amount`() {
        val fee = Fee(fiat = Fiat.Zero, type = FeeType.CreateOnSendWithdrawal)

        assertEquals(0L, fee.fiat.quarks)
        assertEquals(0.0, fee.fiat.decimalValue, 0.001)
    }

    @Test
    fun `two Fees with same data are equal`() {
        val fee1 = Fee(fiat = Fiat(fiat = 1.0), type = FeeType.CreateOnSendWithdrawal)
        val fee2 = Fee(fiat = Fiat(fiat = 1.0), type = FeeType.CreateOnSendWithdrawal)
        assertEquals(fee1, fee2)
    }

    @Test
    fun `two Fees with different amounts are not equal`() {
        val fee1 = Fee(fiat = Fiat(fiat = 1.0), type = FeeType.CreateOnSendWithdrawal)
        val fee2 = Fee(fiat = Fiat(fiat = 2.0), type = FeeType.CreateOnSendWithdrawal)
        assertNotEquals(fee1, fee2)
    }

    @Test
    fun `Fee preserves currency from Fiat`() {
        val eurFiat = Fiat(fiat = 0.50, currencyCode = CurrencyCode.EUR)
        val fee = Fee(fiat = eurFiat, type = FeeType.CreateOnSendWithdrawal)

        assertEquals(CurrencyCode.EUR, fee.fiat.currencyCode)
        assertEquals(0.50, fee.fiat.decimalValue, 0.001)
    }

    @Test
    fun `Fee copy changes only specified fields`() {
        val original = Fee(fiat = Fiat(fiat = 1.0), type = FeeType.CreateOnSendWithdrawal)
        val modified = original.copy(fiat = Fiat(fiat = 2.0))

        assertEquals(2.0, modified.fiat.decimalValue, 0.001)
        assertEquals(FeeType.CreateOnSendWithdrawal, modified.type)
    }
}
