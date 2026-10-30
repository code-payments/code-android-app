package com.flipcash.app.balance.internal

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceViewModelStateTest {

    private val reduce = WalletViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has null provider`() {
        assertNull(WalletViewModel.State().preferredOnRampProvider)
    }

    @Test
    fun `OnPreferredOnRampProviderChanged updates provider`() {
        val provider = OnRampProvider.ManualDeposit
        val updated = reduce(
            WalletViewModel.Event.OnPreferredOnRampProviderChanged(provider)
        )(WalletViewModel.State())
        assertEquals(provider, updated.preferredOnRampProvider)
    }

    @Test
    fun `OnPreferredOnRampProviderChanged with null clears provider`() {
        val state = WalletViewModel.State(
            preferredOnRampProvider = OnRampProvider.ManualDeposit
        )
        val updated = reduce(
            WalletViewModel.Event.OnPreferredOnRampProviderChanged(null)
        )(state)
        assertNull(updated.preferredOnRampProvider)
    }

    @Test
    fun `OpenCurrencySelection is no-op`() {
        val state = WalletViewModel.State(
            preferredOnRampProvider = OnRampProvider.ManualDeposit
        )
        val updated = reduce(WalletViewModel.Event.OpenCurrencySelection)(state)
        assertEquals(state, updated)
    }
}
