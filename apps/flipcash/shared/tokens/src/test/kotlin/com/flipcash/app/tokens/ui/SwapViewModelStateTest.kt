package com.flipcash.app.tokens.ui

import com.flipcash.app.core.tokens.SwapPurpose
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.view.LoadingSuccessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwapViewModelStateTest {

    private val reduce = SwapViewModel.Companion.updateStateForEvent
    private fun mint() = Mint(ByteArray(32) { 1 }.toList())

    // --- Default state ---

    @Test
    fun `default state`() {
        val state = SwapViewModel.State()
        assertNull(state.purpose)
        assertNull(state.tokenWithBalance)
        assertNull(state.reservesWithBalance)
        assertNull(state.swapId)
        assertNull(state.confirmedNetTransferAmount)
        assertFalse(state.loading)
        assertFalse(state.canTransact)
    }

    // --- State reducers ---

    @Test
    fun `OnPurposeChanged sets purpose`() {
        val purpose = SwapPurpose.Buy(mint())
        val updated = reduce(SwapViewModel.Event.OnPurposeChanged(purpose))(SwapViewModel.State())
        assertEquals(purpose, updated.purpose)
    }

    @Test
    fun `OnAmountChanged updates amount model in entry state`() {
        val model = AmountAnimatedInputUiModel(lastPressedBackspace = true)
        val updated = reduce(SwapViewModel.Event.OnAmountChanged(model))(SwapViewModel.State())
        assertEquals(model, updated.amountEntryState.amountAnimatedModel)
    }

    @Test
    fun `OnAmountAccepted sets selectedAmount and confirmedNetTransferAmount`() {
        val amount = VerifiedFiat(LocalFiat.Zero, null)
        val net = Fiat(10.0, CurrencyCode.USD)
        val updated = reduce(
            SwapViewModel.Event.OnAmountAccepted(amount, net)
        )(SwapViewModel.State())
        assertEquals(amount, updated.amountEntryState.selectedAmount)
        assertEquals(net, updated.confirmedNetTransferAmount)
    }

    @Test
    fun `OnMaxDetermined sets maxToAdd`() {
        val updated = reduce(
            SwapViewModel.Event.OnMaxDetermined(max = 500.0, currencyCode = CurrencyCode.EUR)
        )(SwapViewModel.State())
        assertEquals(500.0 to CurrencyCode.EUR, updated.amountEntryState.maxToAdd)
    }

    @Test
    fun `UpdateBuyState sets buy loading and success`() {
        val loading = reduce(
            SwapViewModel.Event.UpdateBuyState(loading = true)
        )(SwapViewModel.State())
        assertTrue(loading.buyProgress.loading)
        assertFalse(loading.buyProgress.success)

        val success = reduce(
            SwapViewModel.Event.UpdateBuyState(loading = false, success = true)
        )(SwapViewModel.State())
        assertTrue(success.buyProgress.success)
        assertFalse(success.buyProgress.loading)
    }

    @Test
    fun `UpdateSellState sets sell loading and success`() {
        val loading = reduce(
            SwapViewModel.Event.UpdateSellState(loading = true)
        )(SwapViewModel.State())
        assertTrue(loading.sellProgress.loading)

        val success = reduce(
            SwapViewModel.Event.UpdateSellState(loading = false, success = true)
        )(SwapViewModel.State())
        assertTrue(success.sellProgress.success)
    }

    @Test
    fun `UpdateProcessingState sets loading, success, and error`() {
        val error = reduce(
            SwapViewModel.Event.UpdateProcessingState(error = true)
        )(SwapViewModel.State())
        assertTrue(error.processingProgress.error)
        assertFalse(error.processingProgress.loading)
    }

    @Test
    fun `OnLimitsChanged sets limits in entry state`() {
        val updated = reduce(
            SwapViewModel.Event.OnLimitsChanged(null)
        )(SwapViewModel.State())
        assertNull(updated.amountEntryState.limits)
    }

    @Test
    fun `OnCurrencyChanged sets currencyModel in entry state`() {
        val currency = Currency(code = "GBP", name = "British Pound", symbol = "£", rate = 0.79)
        val updated = reduce(
            SwapViewModel.Event.OnCurrencyChanged(currency)
        )(SwapViewModel.State())
        assertNotNull(updated.amountEntryState.currencyModel.selected)
        assertEquals("GBP", updated.amountEntryState.currencyModel.selected?.code)
        assertEquals(CurrencyCode.GBP, updated.amountEntryState.currencyModel.code)
    }

    @Test
    fun `OnSwapIdChanged sets swapId`() {
        val swapId = SwapId(ByteArray(32) { 2 }.toList())
        val updated = reduce(
            SwapViewModel.Event.OnSwapIdChanged(swapId)
        )(SwapViewModel.State())
        assertEquals(swapId, updated.swapId)
    }

    // --- No-op events ---

    @Test
    fun `no-op events return state unchanged`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Buy(mint()))
        val noOpEvents = listOf(
            SwapViewModel.Event.OnAmountConfirmed,
            SwapViewModel.Event.OnBackspace,
            SwapViewModel.Event.OnDecimalPressed,
            SwapViewModel.Event.OnEnteredNumberChanged(),
            SwapViewModel.Event.OnNumberPressed(3),
            SwapViewModel.Event.OnSellConfirmed,
            SwapViewModel.Event.ProceedWithPurchase(VerifiedFiat(LocalFiat.Zero, null)),
            SwapViewModel.Event.ProceedWithSale(VerifiedFiat(LocalFiat.Zero, null)),
            SwapViewModel.Event.OnTransactionSuccessful,
            SwapViewModel.Event.ShowSellReceipt,
            SwapViewModel.Event.Exit,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }

    // --- Computed: tokenBalance ---

    @Test
    fun `tokenBalance is Zero when tokenWithBalance is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().tokenBalance)
    }

    // --- Computed: reservesBalance ---

    @Test
    fun `reservesBalance is Zero when reservesWithBalance is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().reservesBalance)
    }

    // --- Computed: canTransact ---

    @Test
    fun `canTransact is false when amount is zero`() {
        assertFalse(SwapViewModel.State().canTransact)
    }

    @Test
    fun `canTransact is false when buyProgress is loading`() {
        val state = SwapViewModel.State(
            buyProgress = LoadingSuccessState(loading = true)
        )
        assertFalse(state.canTransact)
    }

    @Test
    fun `canTransact is false when sellProgress is loading`() {
        val state = SwapViewModel.State(
            sellProgress = LoadingSuccessState(loading = true)
        )
        assertFalse(state.canTransact)
    }

    @Test
    fun `canTransact is false when processingProgress is loading`() {
        val state = SwapViewModel.State(
            processingProgress = LoadingSuccessState(loading = true)
        )
        assertFalse(state.canTransact)
    }

    // --- Computed: sellFee ---

    @Test
    fun `sellFee is null when tokenWithBalance is null`() {
        assertNull(SwapViewModel.State().sellFee)
    }

    // --- Computed: tokenName ---

    @Test
    fun `tokenName is empty when tokenWithBalance is null`() {
        assertEquals("", SwapViewModel.State().tokenName)
    }

    // --- Computed: maxAvailableToSwap ---

    @Test
    fun `maxAvailableToSwap is empty when purpose is null`() {
        assertEquals("", SwapViewModel.State().maxAvailableToSwap)
    }

    // --- Computed: netTransferAmount ---

    @Test
    fun `netTransferAmount uses confirmedNetTransferAmount when set`() {
        val confirmed = Fiat(42.0, CurrencyCode.USD)
        val state = SwapViewModel.State(confirmedNetTransferAmount = confirmed)
        assertEquals(confirmed, state.netTransferAmount)
    }

    // --- Computed: netTransferAmount ---

    @Test
    fun `netTransferAmount falls back to enteredAmount for BalanceIncrease purpose`() {
        // Buy is a BalanceIncrease, so netTransferAmount = enteredAmount when confirmedNetTransferAmount is null
        val state = SwapViewModel.State(purpose = SwapPurpose.Buy(mint()))
        // Default enteredAmount is Fiat(0.0, CurrencyCode.USD) since tokenBalance is Zero
        assertEquals(state.enteredAmount, state.netTransferAmount)
    }

    @Test
    fun `netTransferAmount subtracts fee for non-BalanceIncrease purpose`() {
        // Sell is BalanceDecrease, so netTransferAmount = enteredAmount - feeAmount
        // With no tokenWithBalance, sellFee is null, feeAmount = Zero, so net = entered - 0 = entered
        val state = SwapViewModel.State(purpose = SwapPurpose.Sell(mint()))
        assertEquals(state.enteredAmount.decimalValue - state.feeAmount.decimalValue, state.netTransferAmount.decimalValue, 0.001)
    }

    // --- Computed: enteredAmount ---

    @Test
    fun `enteredAmount defaults to zero with USD currency`() {
        val state = SwapViewModel.State()
        assertEquals(0.0, state.enteredAmount.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, state.enteredAmount.currencyCode)
    }

    // --- Computed: feeAmount ---

    @Test
    fun `feeAmount is Zero when sellFee is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().feeAmount)
    }

    // --- Computed: maxAvailableToSwap per purpose ---

    @Test
    fun `maxAvailableToSwap for Buy uses maxToAdd`() {
        val state = SwapViewModel.State(
            purpose = SwapPurpose.Buy(mint()),
            amountEntryState = AmountEntryState(maxToAdd = 200.0 to CurrencyCode.USD)
        )
        assertTrue(state.maxAvailableToSwap.isNotEmpty())
    }

    @Test
    fun `maxAvailableToSwap for Buy is empty when maxToAdd is null`() {
        val state = SwapViewModel.State(
            purpose = SwapPurpose.Buy(mint()),
        )
        assertEquals("", state.maxAvailableToSwap)
    }

    // --- Computed: transactionLimit per purpose ---

    @Test
    fun `transactionLimit for Sell is tokenBalance`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Sell(mint()))
        // tokenWithBalance is null → tokenBalance = Fiat.Zero
        assertEquals(Fiat.Zero, state.transactionLimit)
    }

    // --- Computed: isError ---

    @Test
    fun `isError is false when amount is empty`() {
        assertFalse(SwapViewModel.State().isError)
    }

    // --- Computed: transactionLimit ---

    @Test
    fun `transactionLimit is Zero when purpose is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().transactionLimit)
    }
}
