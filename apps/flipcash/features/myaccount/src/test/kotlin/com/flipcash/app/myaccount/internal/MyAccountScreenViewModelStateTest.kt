package com.flipcash.app.myaccount.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyAccountScreenViewModelStateTest {

    private val reduce = MyAccountScreenViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has beta disabled`() {
        val state = MyAccountScreenViewModel.State()
        assertFalse(state.isBetaEnabled)
    }

    @Test
    fun `OnBetaFeaturesUnlocked true enables beta and shows ContactMethods item`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(true)
        )(MyAccountScreenViewModel.State())
        assertTrue(updated.isBetaEnabled)
        assertTrue(updated.items.any { it is UserProfile })
    }

    @Test
    fun `OnBetaFeaturesUnlocked false disables beta and hides ContactMethods item`() {
        val state = MyAccountScreenViewModel.State(isBetaEnabled = true)
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(false)
        )(state)
        assertFalse(updated.isBetaEnabled)
        assertFalse(updated.items.any { it is UserProfile })
    }

    @Test
    fun `menu always contains AccessKey LogOut and DeleteAccount`() {
        val withBeta = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(true)
        )(MyAccountScreenViewModel.State())
        assertTrue(withBeta.items.any { it is AccessKey })
        assertTrue(withBeta.items.any { it is LogOut })
        assertTrue(withBeta.items.any { it is DeleteAccount })

        val withoutBeta = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(false)
        )(MyAccountScreenViewModel.State())
        assertTrue(withoutBeta.items.any { it is AccessKey })
        assertTrue(withoutBeta.items.any { it is LogOut })
        assertTrue(withoutBeta.items.any { it is DeleteAccount })
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = MyAccountScreenViewModel.State(isBetaEnabled = true)
        val noOpEvents = listOf(
            MyAccountScreenViewModel.Event.OnLogOutClicked,
            MyAccountScreenViewModel.Event.OnLoggedOutCompletely,
            MyAccountScreenViewModel.Event.OnContactMethodsClicked,
            MyAccountScreenViewModel.Event.OnViewUserProfile,
            MyAccountScreenViewModel.Event.OnViewAccessKey,
            MyAccountScreenViewModel.Event.OnDeleteAccountClicked,
            MyAccountScreenViewModel.Event.OnAccountDeleted,
            MyAccountScreenViewModel.Event.OnAccessKeyClicked,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }
}
