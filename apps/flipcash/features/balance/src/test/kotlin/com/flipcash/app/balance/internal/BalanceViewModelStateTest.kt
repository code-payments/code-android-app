package com.flipcash.app.balance.internal

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceViewModelStateTest {

    private val reduce = BalanceViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has null provider`() {
        assertNull(BalanceViewModel.State().preferredOnRampProvider)
    }

    @Test
    fun `OnPreferredOnRampProviderChanged updates provider`() {
        val provider = OnRampProvider.ManualDeposit
        val updated = reduce(
            BalanceViewModel.Event.OnPreferredOnRampProviderChanged(provider)
        )(BalanceViewModel.State())
        assertEquals(provider, updated.preferredOnRampProvider)
    }

    @Test
    fun `OnPreferredOnRampProviderChanged with null clears provider`() {
        val state = BalanceViewModel.State(
            preferredOnRampProvider = OnRampProvider.ManualDeposit
        )
        val updated = reduce(
            BalanceViewModel.Event.OnPreferredOnRampProviderChanged(null)
        )(state)
        assertNull(updated.preferredOnRampProvider)
    }

    @Test
    fun `OpenCurrencySelection is no-op`() {
        val state = BalanceViewModel.State(
            preferredOnRampProvider = OnRampProvider.ManualDeposit
        )
        val updated = reduce(BalanceViewModel.Event.OpenCurrencySelection)(state)
        assertEquals(state, updated)
    }
}
