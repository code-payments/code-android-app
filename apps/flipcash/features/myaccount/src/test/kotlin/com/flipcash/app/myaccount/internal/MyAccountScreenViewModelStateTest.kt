package com.flipcash.app.myaccount.internal

import com.flipcash.app.myaccount.internal.myaccount.Blocklist
import com.flipcash.app.myaccount.internal.myaccount.MyAccountScreenViewModel
import com.flipcash.app.myaccount.internal.myaccount.RequireBiometrics
import com.flipcash.app.myaccount.internal.myaccount.UserProfile
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
    fun `user profile stays hidden until beta features unlock`() {
        val locked = MyAccountScreenViewModel.State()
        assertFalse(locked.items.any { it is UserProfile })

        val unlocked = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(unlocked = true)
        )(locked)

        assertTrue(unlocked.items.any { it is UserProfile })
        assertTrue(unlocked.items.any { it is RequireBiometrics })
    }

    @Test
    fun `unlocking beta keeps an unsupported biometrics row hidden`() {
        val noBiometrics = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = false,
                available = false,
            )
        )(MyAccountScreenViewModel.State())

        val unlocked = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(unlocked = true)
        )(noBiometrics)

        assertFalse(unlocked.items.any { it is RequireBiometrics })
        assertTrue(unlocked.items.any { it is UserProfile })
    }

    @Test
    fun `biometrics changes keep an unlocked user profile visible`() {
        val unlocked = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(unlocked = true)
        )(MyAccountScreenViewModel.State())

        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = true,
                supported = true,
                available = true,
            )
        )(unlocked)

        assertTrue(updated.items.any { it is UserProfile })
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
