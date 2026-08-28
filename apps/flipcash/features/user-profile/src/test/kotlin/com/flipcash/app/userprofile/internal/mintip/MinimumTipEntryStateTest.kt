package com.flipcash.app.userprofile.internal.mintip

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinimumTipEntryStateTest {

    private val reduce = MinimumTipEntryViewModel.updateStateForEvent

    private fun apply(
        event: MinimumTipEntryViewModel.Event,
        state: MinimumTipEntryViewModel.State = MinimumTipEntryViewModel.State(),
    ) = reduce(event)(state)

    @Test
    fun `nothing is saved by default`() {
        val state = MinimumTipEntryViewModel.State()
        assertEquals(null, state.saved)
        assertTrue(state.saving.isIdle)
    }

    @Test
    fun `the resolved fee lands in state`() {
        val fee = Fiat(5.0, CurrencyCode.USD)
        assertEquals(fee, apply(MinimumTipEntryViewModel.Event.SavedFeeChanged(fee)).saved)
    }

    @Test
    fun `clearing the fee empties it again`() {
        val withFee = apply(
            MinimumTipEntryViewModel.Event.SavedFeeChanged(Fiat(5.0, CurrencyCode.USD))
        )
        assertEquals(null, apply(MinimumTipEntryViewModel.Event.SavedFeeChanged(null), withFee).saved)
    }

    @Test
    fun `the preferred rate sets the entry currency`() {
        val updated = apply(MinimumTipEntryViewModel.Event.CurrencyChanged(CurrencyCode.EUR))
        assertEquals(CurrencyCode.EUR, updated.currency)
    }

    @Test
    fun `saving runs loading then success then back to idle`() {
        val loading = apply(MinimumTipEntryViewModel.Event.UpdateSavingState(loading = true))
        assertTrue(loading.saving.loading)
        assertFalse(loading.saving.isIdle)

        val success = apply(
            MinimumTipEntryViewModel.Event.UpdateSavingState(success = true),
            loading,
        )
        assertTrue(success.saving.success)
        assertFalse(success.saving.loading)

        assertTrue(apply(MinimumTipEntryViewModel.Event.UpdateSavingState(), success).saving.isIdle)
    }

    @Test
    fun `the navigation events leave state alone`() {
        val state = MinimumTipEntryViewModel.State(saved = Fiat(5.0, CurrencyCode.USD))
        listOf(
            MinimumTipEntryViewModel.Event.ConfirmRequested,
            MinimumTipEntryViewModel.Event.Saved,
        ).forEach { event ->
            assertEquals(state, apply(event, state), "Event $event should be no-op")
        }
    }
}
