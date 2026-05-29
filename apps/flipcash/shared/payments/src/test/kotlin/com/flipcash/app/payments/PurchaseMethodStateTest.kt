package com.flipcash.app.payments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PurchaseMethodStateTest {

    @Test
    fun defaultStateHasOnlyPhantomWallet() {
        val state = PurchaseMethodState()
        assertEquals(1, state.availableMethods.size)
        assertIs<PurchaseMethod.PhantomWallet>(state.availableMethods[0])
    }

    @Test
    fun hasReservesIsFalseForZeroBalance() {
        val state = PurchaseMethodState()
        assertFalse(state.hasReserves)
    }

    @Test
    fun coinbaseOnRampIncludedWhenAvailable() {
        val state = PurchaseMethodState(coinbaseOnRampAvailable = true)
        assertTrue(state.availableMethods.any { it is PurchaseMethod.CoinbaseOnRamp })
    }

    @Test
    fun coinbaseOnRampExcludedWhenUnavailable() {
        val state = PurchaseMethodState(coinbaseOnRampAvailable = false)
        assertFalse(state.availableMethods.any { it is PurchaseMethod.CoinbaseOnRamp })
    }

    @Test
    fun otherWalletIncludedWhenAllowed() {
        val state = PurchaseMethodState(canUseOtherWallets = true)
        assertTrue(state.availableMethods.any { it is PurchaseMethod.OtherWallet })
    }

    @Test
    fun otherWalletExcludedWhenNotAllowed() {
        val state = PurchaseMethodState(canUseOtherWallets = false)
        assertFalse(state.availableMethods.any { it is PurchaseMethod.OtherWallet })
    }

    @Test
    fun cashReservesExcludedWhenZeroBalance() {
        val state = PurchaseMethodState()
        assertFalse(state.availableMethods.any { it is PurchaseMethod.CashReserves })
    }

    @Test
    fun phantomWalletAlwaysPresent() {
        val state = PurchaseMethodState(
            coinbaseOnRampAvailable = true,
            canUseOtherWallets = true,
        )
        assertTrue(state.availableMethods.any { it is PurchaseMethod.PhantomWallet })
    }

    @Test
    fun orderingCoinbaseBeforePhantomBeforeOther() {
        val state = PurchaseMethodState(
            coinbaseOnRampAvailable = true,
            canUseOtherWallets = true,
        )
        val methods = state.availableMethods
        val coinbaseIdx = methods.indexOfFirst { it is PurchaseMethod.CoinbaseOnRamp }
        val phantomIdx = methods.indexOfFirst { it is PurchaseMethod.PhantomWallet }
        val otherIdx = methods.indexOfFirst { it is PurchaseMethod.OtherWallet }
        assertTrue(coinbaseIdx < phantomIdx)
        assertTrue(phantomIdx < otherIdx)
    }
}
