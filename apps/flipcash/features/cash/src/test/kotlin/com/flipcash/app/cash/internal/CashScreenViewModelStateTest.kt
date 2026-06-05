package com.flipcash.app.cash.internal

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashScreenViewModelStateTest {

    private val reduce = CashScreenViewModel.Companion.updateStateForEvent
    private fun mint() = Mint(ByteArray(32) { 1 }.toList())

    // --- Default state ---

    @Test
    fun `default state has null token and address`() {
        val state = CashScreenViewModel.State()
        assertNull(state.selectedTokenAddress)
        assertNull(state.token)
        assertNull(state.limits)
    }

    // --- State reducers ---

    @Test
    fun `OnTokenSelected sets selectedTokenAddress`() {
        val mint = mint()
        val updated = reduce(CashScreenViewModel.Event.OnTokenSelected(mint))(CashScreenViewModel.State())
        assertEquals(mint, updated.selectedTokenAddress)
    }

    @Test
    fun `UpdateLoadingState sets loading`() {
        val updated = reduce(
            CashScreenViewModel.Event.UpdateLoadingState(loading = true)
        )(CashScreenViewModel.State())
        assertTrue(updated.generatingBill.loading)
        kotlin.test.assertFalse(updated.generatingBill.success)
    }

    @Test
    fun `UpdateLoadingState sets success`() {
        val updated = reduce(
            CashScreenViewModel.Event.UpdateLoadingState(loading = false, success = true)
        )(CashScreenViewModel.State())
        assertTrue(updated.generatingBill.success)
        kotlin.test.assertFalse(updated.generatingBill.loading)
    }

    @Test
    fun `OnLimitsChanged sets limits`() {
        val updated = reduce(
            CashScreenViewModel.Event.OnLimitsChanged(null)
        )(CashScreenViewModel.State())
        assertNull(updated.limits)
    }

    @Test
    fun `OnCurrencyChanged sets currencyModel`() {
        val currency = Currency(code = "EUR", name = "Euro", symbol = "€", rate = 0.92)
        val updated = reduce(
            CashScreenViewModel.Event.OnCurrencyChanged(currency)
        )(CashScreenViewModel.State())
        assertNotNull(updated.currencyModel.selected)
        assertEquals("EUR", updated.currencyModel.selected?.code)
        assertEquals(CurrencyCode.EUR, updated.currencyModel.code)
    }

    @Test
    fun `OnCurrencyChanged preserves fractionUnits`() {
        val currency = Currency(code = "JPY", name = "Japanese Yen", symbol = "¥", fractionUnits = 0)
        val updated = reduce(
            CashScreenViewModel.Event.OnCurrencyChanged(currency)
        )(CashScreenViewModel.State())
        assertEquals(0, updated.currencyModel.fractionUnits)
    }

    // --- No-op events ---

    @Test
    fun `no-op events return state unchanged`() {
        val state = CashScreenViewModel.State(selectedTokenAddress = mint())
        val noOpEvents = listOf(
            CashScreenViewModel.Event.InitializeToken(null),
            CashScreenViewModel.Event.OnGive,
            CashScreenViewModel.Event.AddCashToWallet(Fiat.Zero),
            CashScreenViewModel.Event.OpenScreen(com.flipcash.app.core.AppRoute.Loading),
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }

}
