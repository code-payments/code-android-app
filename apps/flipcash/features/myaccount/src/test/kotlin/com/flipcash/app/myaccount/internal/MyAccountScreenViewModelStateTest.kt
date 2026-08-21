package com.flipcash.app.myaccount.internal

import com.flipcash.app.myaccount.internal.myaccount.Blocklist
import com.flipcash.app.myaccount.internal.myaccount.MyAccountScreenViewModel
import com.flipcash.app.myaccount.internal.myaccount.RequireBiometrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyAccountScreenViewModelStateTest {

    private val reduce = MyAccountScreenViewModel.Companion.updateStateForEvent

    @Test
    fun `default state lists biometrics and blocklist`() {
        val state = MyAccountScreenViewModel.State()
        assertEquals(listOf(RequireBiometrics, Blocklist), state.items)
        assertFalse(state.biometricsRequired)
    }

    @Test
    fun `unsupported biometrics hides the row`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = false,
                available = false,
            )
        )(MyAccountScreenViewModel.State())

        assertFalse(updated.items.any { it is RequireBiometrics })
        assertTrue(updated.items.any { it is Blocklist })
    }

    @Test
    fun `enrolled biometrics keeps the row and mirrors the setting`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = true,
                supported = true,
                available = true,
            )
        )(MyAccountScreenViewModel.State())

        assertTrue(updated.items.any { it is RequireBiometrics })
        assertTrue(updated.biometricsRequired)
        assertTrue(updated.biometricsAvailable)
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = MyAccountScreenViewModel.State(biometricsRequired = true)
        val noOpEvents = listOf(
            MyAccountScreenViewModel.Event.OnBiometricsToggled,
            MyAccountScreenViewModel.Event.OnContactMethodsClicked,
            MyAccountScreenViewModel.Event.OnViewUserProfile,
            MyAccountScreenViewModel.Event.OnBlocklistClicked,
            MyAccountScreenViewModel.Event.OnViewBlocklist,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }
}
