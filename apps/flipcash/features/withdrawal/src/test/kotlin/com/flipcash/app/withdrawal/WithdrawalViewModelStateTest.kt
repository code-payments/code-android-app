package com.flipcash.app.withdrawal

import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.view.LoadingSuccessState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for WithdrawalViewModel pure state reducers and computed properties.
 */
class WithdrawalViewModelStateTest {

    private val reduce = WithdrawalViewModel.Companion.updateStateForEvent

    // --- updateStateForEvent ---

    @Test
    fun `OnFeeChanged updates fee amount`() {
        val fee = Fiat(100, CurrencyCode.USD)
        val updated = reduce(WithdrawalViewModel.Event.OnFeeChanged(fee))(WithdrawalViewModel.State())
        assertEquals(fee, updated.feeAmount)
    }

    @Test
    fun `OnFeeChanged with null clears fee`() {
        val state = WithdrawalViewModel.State(feeAmount = Fiat(100, CurrencyCode.USD))
        val updated = reduce(WithdrawalViewModel.Event.OnFeeChanged(null))(state)
        assertEquals(null, updated.feeAmount)
    }

    @Test
    fun `OnEntryRateUpdated sets entry rate`() {
        val rate = Rate(1.35, CurrencyCode.CAD)
        val updated = reduce(WithdrawalViewModel.Event.OnEntryRateUpdated(rate))(WithdrawalViewModel.State())
        assertEquals(rate, updated.entryRate)
    }

    @Test
    fun `OnAmountAccepted updates selected amount and resets confirming state`() {
        val amount = VerifiedFiat(LocalFiat.Zero, null)
        val state = WithdrawalViewModel.State(
            amountEntryState = AmountEntryState(
                confirmingAmount = LoadingSuccessState(loading = true)
            )
        )
        val updated = reduce(WithdrawalViewModel.Event.OnAmountAccepted(amount))(state)
        assertEquals(amount, updated.amountEntryState.selectedAmount)
        assertEquals(false, updated.amountEntryState.confirmingAmount.loading)
    }

    @Test
    fun `UpdateConfirmingAmountState sets loading`() {
        val updated = reduce(
            WithdrawalViewModel.Event.UpdateConfirmingAmountState(loading = true)
        )(WithdrawalViewModel.State())
        assertEquals(true, updated.amountEntryState.confirmingAmount.loading)
    }

    @Test
    fun `UpdateConfirmingAmountState sets success`() {
        val updated = reduce(
            WithdrawalViewModel.Event.UpdateConfirmingAmountState(loading = false, success = true)
        )(WithdrawalViewModel.State())
        assertEquals(true, updated.amountEntryState.confirmingAmount.success)
        assertEquals(false, updated.amountEntryState.confirmingAmount.loading)
    }

    @Test
    fun `UpdateWithdrawalState sets loading and success`() {
        val updated = reduce(
            WithdrawalViewModel.Event.UpdateWithdrawalState(loading = false, success = true)
        )(WithdrawalViewModel.State())
        assertEquals(true, updated.withdrawalState.success)
        assertEquals(false, updated.withdrawalState.loading)
    }

    @Test
    fun `UpdateWithdrawalState sets error`() {
        val updated = reduce(
            WithdrawalViewModel.Event.UpdateWithdrawalState(error = true)
        )(WithdrawalViewModel.State())
        assertEquals(true, updated.withdrawalState.error)
    }

    @Test
    fun `OnAmountChanged updates amount model`() {
        val model = AmountAnimatedInputUiModel(lastPressedBackspace = true)
        val updated = reduce(
            WithdrawalViewModel.Event.OnAmountChanged(model)
        )(WithdrawalViewModel.State())
        assertEquals(model, updated.amountEntryState.amountAnimatedModel)
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = WithdrawalViewModel.State(entryRate = Rate.oneToOne)
        val noOpEvents = listOf(
            WithdrawalViewModel.Event.OnAmountConfirmed,
            WithdrawalViewModel.Event.OnWithdraw,
            WithdrawalViewModel.Event.OnBackspace,
            WithdrawalViewModel.Event.OnDecimalPressed,
            WithdrawalViewModel.Event.PasteFromClipboard,
            WithdrawalViewModel.Event.OnDestinationConfirmed,
            WithdrawalViewModel.Event.OnLearnAboutFee,
            WithdrawalViewModel.Event.OnWithdrawalTooSmall,
            WithdrawalViewModel.Event.ConfirmWithdrawal,
            WithdrawalViewModel.Event.OnWithdrawalConfirmed,
            WithdrawalViewModel.Event.OnWithdrawSuccessful,
            WithdrawalViewModel.Event.ProceedWithWithdrawal,
            WithdrawalViewModel.Event.ProceedWithWithdrawalViaSwapper,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }

    // --- State.canWithdraw ---

    @Test
    fun `canWithdraw false when amount is zero`() {
        val state = WithdrawalViewModel.State()
        assertEquals(false, state.canWithdraw)
    }

    // --- State.feeInEntryCurrency ---

    @Test
    fun `feeInEntryCurrency is null when fee is null`() {
        val state = WithdrawalViewModel.State(feeAmount = null, entryRate = Rate.oneToOne)
        assertEquals(null, state.feeInEntryCurrency)
    }

    @Test
    fun `feeInEntryCurrency is null when entry rate is null`() {
        val state = WithdrawalViewModel.State(feeAmount = Fiat(100, CurrencyCode.USD), entryRate = null)
        assertEquals(null, state.feeInEntryCurrency)
    }

    @Test
    fun `feeInEntryCurrency is non-null when both fee and rate present`() {
        val state = WithdrawalViewModel.State(
            feeAmount = Fiat(100, CurrencyCode.USD),
            entryRate = Rate.oneToOne
        )
        val fee = state.feeInEntryCurrency
        assertEquals(100.0, fee?.decimalValue)
    }

    // --- State.tokenBalance ---

    @Test
    fun `tokenBalance is Zero when token is null`() {
        val state = WithdrawalViewModel.State(token = null)
        assertEquals(Fiat.Zero, state.tokenBalance)
    }
}
