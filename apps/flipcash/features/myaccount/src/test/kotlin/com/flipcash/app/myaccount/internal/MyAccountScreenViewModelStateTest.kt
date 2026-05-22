package com.flipcash.app.myaccount.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MyAccountScreenViewModelStateTest {

    private val reduce = MyAccountScreenViewModel.Companion.updateStateForEvent

    @Test
    fun `default state has null user info and beta disabled`() {
        val state = MyAccountScreenViewModel.State()
        assertNull(state.accountId)
        assertNull(state.publicKey)
        assertNull(state.pushToken)
        assertFalse(state.isBetaEnabled)
        assertFalse(state.showAccountInfo)
    }

    @Test
    fun `OnUserAssociated sets user fields`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnUserAssociated(
                userId = "user-123",
                publicKey = "pk-abc",
                pushToken = "token-xyz"
            )
        )(MyAccountScreenViewModel.State())
        assertEquals("user-123", updated.accountId)
        assertEquals("pk-abc", updated.publicKey)
        assertEquals("token-xyz", updated.pushToken)
    }

    @Test
    fun `OnUserAssociated with null values`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnUserAssociated(
                userId = null,
                publicKey = null,
                pushToken = null
            )
        )(MyAccountScreenViewModel.State())
        assertNull(updated.accountId)
        assertNull(updated.publicKey)
        assertNull(updated.pushToken)
    }

    @Test
    fun `OnBetaFeaturesUnlocked true enables beta and shows all menu items`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(true)
        )(MyAccountScreenViewModel.State())
        assertTrue(updated.isBetaEnabled)
        assertTrue(updated.items.any { it is VerifyPhone })
        assertTrue(updated.items.any { it is VerifyEmail })
    }

    @Test
    fun `OnBetaFeaturesUnlocked false disables beta and hides verification items`() {
        val state = MyAccountScreenViewModel.State(isBetaEnabled = true)
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(false)
        )(state)
        assertFalse(updated.isBetaEnabled)
        assertFalse(updated.items.any { it is VerifyPhone })
        assertFalse(updated.items.any { it is VerifyEmail })
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
    fun `ToggleAccountInfo sets showAccountInfo`() {
        val shown = reduce(
            MyAccountScreenViewModel.Event.ToggleAccountInfo(true)
        )(MyAccountScreenViewModel.State())
        assertTrue(shown.showAccountInfo)

        val hidden = reduce(
            MyAccountScreenViewModel.Event.ToggleAccountInfo(false)
        )(shown)
        assertFalse(hidden.showAccountInfo)
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = MyAccountScreenViewModel.State(accountId = "test", isBetaEnabled = true)
        val noOpEvents = listOf(
            MyAccountScreenViewModel.Event.OnLogOutClicked,
            MyAccountScreenViewModel.Event.OnLoggedOutCompletely,
            MyAccountScreenViewModel.Event.OnVerifyPhoneClicked,
            MyAccountScreenViewModel.Event.OnVerifyEmailClicked,
            MyAccountScreenViewModel.Event.OnViewAccessKey,
            MyAccountScreenViewModel.Event.CopyPublicKey,
            MyAccountScreenViewModel.Event.CopyAccountId,
            MyAccountScreenViewModel.Event.CopyPushToken,
            MyAccountScreenViewModel.Event.OnTitleClicked,
            MyAccountScreenViewModel.Event.OnDeleteAccountClicked,
            MyAccountScreenViewModel.Event.OnAccountDeleted,
            MyAccountScreenViewModel.Event.OnAccessKeyClicked,
        )
        noOpEvents.forEach { event ->
            assertEquals(state, reduce(event)(state), "Event $event should be no-op")
        }
    }
}
