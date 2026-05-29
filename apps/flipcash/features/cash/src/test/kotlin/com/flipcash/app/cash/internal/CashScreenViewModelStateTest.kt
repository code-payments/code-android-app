package com.flipcash.app.cash.internal

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertNull(state.maxForGive)
        assertFalse(state.canGive)
    }

    // --- State reducers ---

    @Test
    fun `OnTokenSelected sets selectedTokenAddress`() {
        val mint = mint()
        val updated = reduce(CashScreenViewModel.Event.OnTokenSelected(mint))(CashScreenViewModel.State())
        assertEquals(mint, updated.selectedTokenAddress)
    }

    @Test
    fun `OnAmountChanged updates amountAnimatedModel`() {
        val model = AmountAnimatedInputUiModel(lastPressedBackspace = true)
        val updated = reduce(CashScreenViewModel.Event.OnAmountChanged(model))(CashScreenViewModel.State())
        assertEquals(model, updated.amountAnimatedModel)
    }

    @Test
    fun `UpdateLoadingState sets loading`() {
        val updated = reduce(
            CashScreenViewModel.Event.UpdateLoadingState(loading = true)
        )(CashScreenViewModel.State())
        assertTrue(updated.generatingBill.loading)
        assertFalse(updated.generatingBill.success)
    }

    @Test
    fun `UpdateLoadingState sets success`() {
        val updated = reduce(
            CashScreenViewModel.Event.UpdateLoadingState(loading = false, success = true)
        )(CashScreenViewModel.State())
        assertTrue(updated.generatingBill.success)
        assertFalse(updated.generatingBill.loading)
    }

    @Test
    fun `OnMaxDetermined sets maxForGive`() {
        val updated = reduce(
            CashScreenViewModel.Event.OnMaxDetermined(max = 50.0, currencyCode = CurrencyCode.USD)
        )(CashScreenViewModel.State())
        assertEquals(50.0 to CurrencyCode.USD, updated.maxForGive)
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
            CashScreenViewModel.Event.OnBackspace,
            CashScreenViewModel.Event.OnGive,
            CashScreenViewModel.Event.OnEnteredNumberChanged(),
            CashScreenViewModel.Event.OnNumberPressed(5),
            CashScreenViewModel.Event.OnDecimalPressed,
            CashScreenViewModel.Event.AddCashToWallet(Fiat.Zero),
            CashScreenViewModel.Event.OpenScreen(com.flipcash.app.core.AppRoute.Loading),
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }

    // --- Computed: canGive ---

    @Test
    fun `canGive is false when amount is zero`() {
        assertFalse(CashScreenViewModel.State().canGive)
    }

    // --- Computed: maxAvailableForGive ---

    @Test
    fun `maxAvailableForGive is empty when maxForGive is null`() {
        val state = CashScreenViewModel.State(maxForGive = null)
        assertEquals("", state.maxAvailableForGive)
    }

    @Test
    fun `maxAvailableForGive is formatted when maxForGive is set`() {
        val state = CashScreenViewModel.State(maxForGive = 100.0 to CurrencyCode.USD)
        assertTrue(state.maxAvailableForGive.isNotEmpty())
    }

    // --- Computed: isError ---

    @Test
    fun `isError is true when maxForGive is null`() {
        // Default amountData has "0" text (not empty), and maxForGive is null → error
        assertTrue(CashScreenViewModel.State().isError)
    }

    @Test
    fun `isError is false when amount within maxForGive`() {
        // Default amount 0.0 <= 100.0
        val state = CashScreenViewModel.State(maxForGive = 100.0 to CurrencyCode.USD)
        assertFalse(state.isError)
    }
}
