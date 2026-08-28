package com.flipcash.app.tipping.internal

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetMinimumTipStateTest {

    private val reduce = SetMinimumTipViewModel.updateStateForEvent

    private fun apply(
        event: SetMinimumTipViewModel.Event,
        state: SetMinimumTipViewModel.State = SetMinimumTipViewModel.State(),
    ) = reduce(event)(state)

    @Test
    fun `nothing is saved by default`() {
        val state = SetMinimumTipViewModel.State()
        assertEquals(null, state.saved)
        assertTrue(state.saving.isIdle)
    }

    @Test
    fun `the resolved fee lands in state`() {
        val fee = Fiat(5.0, CurrencyCode.USD)
        assertEquals(fee, apply(SetMinimumTipViewModel.Event.SavedFeeChanged(fee)).saved)
    }

    @Test
    fun `clearing the fee empties it again`() {
        val withFee = apply(
            SetMinimumTipViewModel.Event.SavedFeeChanged(Fiat(5.0, CurrencyCode.USD))
        )
        assertEquals(null, apply(SetMinimumTipViewModel.Event.SavedFeeChanged(null), withFee).saved)
    }

    @Test
    fun `the preferred rate sets the entry currency`() {
        val updated = apply(SetMinimumTipViewModel.Event.CurrencyChanged(CurrencyCode.EUR))
        assertEquals(CurrencyCode.EUR, updated.currency)
    }

    @Test
    fun `saving runs loading then success then back to idle`() {
        val loading = apply(SetMinimumTipViewModel.Event.UpdateSavingState(loading = true))
        assertTrue(loading.saving.loading)
        assertFalse(loading.saving.isIdle)

        val success = apply(
            SetMinimumTipViewModel.Event.UpdateSavingState(success = true),
            loading,
        )
        assertTrue(success.saving.success)
        assertFalse(success.saving.loading)

        assertTrue(apply(SetMinimumTipViewModel.Event.UpdateSavingState(), success).saving.isIdle)
    }

    @Test
    fun `the navigation events leave state alone`() {
        val state = SetMinimumTipViewModel.State(saved = Fiat(5.0, CurrencyCode.USD))
        listOf(
            SetMinimumTipViewModel.Event.ConfirmRequested,
            SetMinimumTipViewModel.Event.Saved,
        ).forEach { event ->
            assertEquals(state, apply(event, state), "Event $event should be no-op")
        }
    }
}
