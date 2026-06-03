package com.flipcash.app.tokens.ui

import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.view.LoadingSuccessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(state.canTransact)
    }

    // --- State reducers ---

    @Test
    fun `OnPurposeChanged sets purpose`() {
        val purpose = SwapPurpose.Buy(mint())
        val updated = reduce(SwapViewModel.Event.OnPurposeChanged(purpose))(SwapViewModel.State())
        assertEquals(purpose, updated.purpose)
    }

    @Test
    fun `OnAmountAccepted sets selectedAmount, confirmedNetTransferAmount, enteredAmount, and feeAmount`() {
        val amount = VerifiedFiat(LocalFiat.Zero, null)
        val net = Fiat(10.0, CurrencyCode.USD)
        val entered = Fiat(12.0, CurrencyCode.USD)
        val fee = Fiat(2.0, CurrencyCode.USD)
        val updated = reduce(
            SwapViewModel.Event.OnAmountAccepted(amount, net, entered, fee)
        )(SwapViewModel.State())
        assertEquals(amount, updated.amountEntryState.selectedAmount)
        assertEquals(net, updated.confirmedNetTransferAmount)
        assertEquals(entered, updated.confirmedEnteredAmount)
        assertEquals(fee, updated.confirmedFeeAmount)
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
    fun `OnSwapIdChanged sets swapId`() {
        val swapId = SwapId(ByteArray(32) { 2 }.toList())
        val updated = reduce(
            SwapViewModel.Event.OnSwapIdChanged(swapId)
        )(SwapViewModel.State())
        assertEquals(swapId, updated.swapId)
    }

    @Test
    fun `OnInitialAmountProvided sets minimumBuyAmount and pendingInitialAmount`() {
        val amount = Fiat(5.0, CurrencyCode.USD)
        val updated = reduce(
            SwapViewModel.Event.OnInitialAmountProvided(amount)
        )(SwapViewModel.State())
        assertEquals(amount, updated.minimumBuyAmount)
        assertEquals(amount, updated.pendingInitialAmount)
    }

    @Test
    fun `OnInitialAmountEntered clears pendingInitialAmount`() {
        val state = SwapViewModel.State(pendingInitialAmount = Fiat(5.0, CurrencyCode.USD))
        val updated = reduce(SwapViewModel.Event.OnInitialAmountEntered)(state)
        assertNull(updated.pendingInitialAmount)
    }

    @Test
    fun `CoinbaseSelected sets Buy funding source to Coinbase`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Buy(mint()))
        val updated = reduce(SwapViewModel.Event.CoinbaseSelected)(state)
        assertEquals(FundingSource.Coinbase, (updated.purpose as SwapPurpose.Buy).fundingSource)
    }

    @Test
    fun `PhantomSelected sets Buy funding source to Phantom`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Buy(mint()))
        val updated = reduce(SwapViewModel.Event.PhantomSelected)(state)
        assertEquals(FundingSource.Phantom, (updated.purpose as SwapPurpose.Buy).fundingSource)
    }

    @Test
    fun `CoinbaseSelected is no-op for non-Buy purpose`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Sell(mint()))
        val updated = reduce(SwapViewModel.Event.CoinbaseSelected)(state)
        assertEquals(state, updated)
    }

    // --- No-op events ---

    @Test
    fun `no-op events return state unchanged`() {
        val state = SwapViewModel.State(purpose = SwapPurpose.Buy(mint()))
        val noOpEvents = listOf(
            SwapViewModel.Event.OnAmountConfirmed,
            SwapViewModel.Event.OnSellConfirmed,
            SwapViewModel.Event.OtherWalletSelected,
            SwapViewModel.Event.ConfirmPhantomTransaction,
            SwapViewModel.Event.StartPhantomCeremony,
            SwapViewModel.Event.PhantomConnected,
            SwapViewModel.Event.PhantomCeremonyFailed,
            SwapViewModel.Event.OnCurrencyChanged(Currency(code = "USD", name = "US Dollar", symbol = "$", rate = 1.0)),
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
    fun `canTransact is true when all progress states are idle`() {
        assertTrue(SwapViewModel.State().canTransact)
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

    // --- Computed: enteredAmount ---

    @Test
    fun `enteredAmount defaults to Zero when confirmedEnteredAmount is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().enteredAmount)
    }

    @Test
    fun `enteredAmount returns confirmedEnteredAmount when set`() {
        val amount = Fiat(42.0, CurrencyCode.USD)
        val state = SwapViewModel.State(confirmedEnteredAmount = amount)
        assertEquals(amount, state.enteredAmount)
    }

    // --- Computed: feeAmount ---

    @Test
    fun `feeAmount is Zero when confirmedFeeAmount is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().feeAmount)
    }

    // --- Computed: netTransferAmount ---

    @Test
    fun `netTransferAmount uses confirmedNetTransferAmount when set`() {
        val confirmed = Fiat(42.0, CurrencyCode.USD)
        val state = SwapViewModel.State(confirmedNetTransferAmount = confirmed)
        assertEquals(confirmed, state.netTransferAmount)
    }

    @Test
    fun `netTransferAmount is Zero when confirmedNetTransferAmount is null`() {
        assertEquals(Fiat.Zero, SwapViewModel.State().netTransferAmount)
    }
}
