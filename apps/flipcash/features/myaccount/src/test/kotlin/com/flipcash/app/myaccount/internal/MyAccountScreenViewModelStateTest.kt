package com.flipcash.app.myaccount.internal

import com.flipcash.app.myaccount.internal.myaccount.Blocklist
import com.flipcash.app.myaccount.internal.myaccount.ChangeDisplayName
import com.flipcash.app.myaccount.internal.myaccount.ChangeUsername
import com.flipcash.app.myaccount.internal.myaccount.MyAccountScreenViewModel
import com.flipcash.app.myaccount.internal.myaccount.ProfilePicture
import com.flipcash.app.myaccount.internal.myaccount.RequireBiometrics
import com.flipcash.app.myaccount.internal.myaccount.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyAccountScreenViewModelStateTest {

    private val reduce = MyAccountScreenViewModel.Companion.updateStateForEvent

    private fun claimed(state: MyAccountScreenViewModel.State) =
        reduce(MyAccountScreenViewModel.Event.OnUsernameClaimChanged(claimed = true))(state)

    @Test
    fun `default state lists the display name, profile picture, biometrics and blocklist`() {
        val state = MyAccountScreenViewModel.State()
        assertEquals(
            listOf(ChangeDisplayName, ProfilePicture, RequireBiometrics, Blocklist),
            state.items,
        )
        assertFalse(state.biometricsRequired)
    }

    @Test
    fun `the profile picture row carries no condition`() {
        val noBiometrics = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = false,
                available = false,
            )
        )(MyAccountScreenViewModel.State())

        assertTrue(noBiometrics.items.any { it is ProfilePicture })
        assertTrue(claimed(noBiometrics).items.any { it is ProfilePicture })
    }

    @Test
    fun `changing the username is offered only once a handle is claimed`() {
        val unclaimed = MyAccountScreenViewModel.State()
        assertFalse(unclaimed.usernameClaimed)
        assertFalse(unclaimed.items.any { it is ChangeUsername })

        val withHandle = claimed(unclaimed)

        assertTrue(withHandle.usernameClaimed)
        assertEquals(
            listOf(ChangeDisplayName, ChangeUsername, ProfilePicture, RequireBiometrics, Blocklist),
            withHandle.items,
        )
    }

    @Test
    fun `losing the handle takes the username row back out`() {
        val dropped = reduce(
            MyAccountScreenViewModel.Event.OnUsernameClaimChanged(claimed = false)
        )(claimed(MyAccountScreenViewModel.State()))

        assertFalse(dropped.usernameClaimed)
        assertFalse(dropped.items.any { it is ChangeUsername })
    }

    @Test
    fun `biometrics changes keep a claimed username row visible`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = true,
                supported = true,
                available = true,
            )
        )(claimed(MyAccountScreenViewModel.State()))

        assertTrue(updated.items.any { it is ChangeUsername })
    }

    @Test
    fun `unlocking beta keeps a claimed username row visible`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBetaFeaturesUnlocked(unlocked = true)
        )(claimed(MyAccountScreenViewModel.State()))

        assertTrue(updated.items.any { it is ChangeUsername })
        assertTrue(updated.items.any { it is UserProfile })
    }

    @Test
    fun `claiming a handle leaves the other rows' conditions alone`() {
        val noBiometrics = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = false,
                available = false,
            )
        )(MyAccountScreenViewModel.State())

        val withHandle = claimed(noBiometrics)

        assertTrue(withHandle.items.any { it is ChangeUsername })
        assertFalse(withHandle.items.any { it is RequireBiometrics })
        assertFalse(withHandle.items.any { it is UserProfile })
    }

    @Test
    fun `changing the display name is offered without the beta unlock`() {
        val locked = MyAccountScreenViewModel.State()
        assertTrue(locked.items.any { it is ChangeDisplayName })

        val noBiometrics = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = false,
                available = false,
            )
        )(locked)

        assertTrue(noBiometrics.items.any { it is ChangeDisplayName })
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
    fun `unavailable biometrics carries the explanation through`() {
        val updated = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = true,
                available = false,
                description = 42,
            )
        )(MyAccountScreenViewModel.State())

        assertTrue(updated.items.any { it is RequireBiometrics })
        assertFalse(updated.biometricsAvailable)
        assertEquals(42, updated.biometricsDescription)
    }

    @Test
    fun `enrolling biometrics clears the explanation`() {
        val unavailable = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = true,
                available = false,
                description = 42,
            )
        )(MyAccountScreenViewModel.State())

        val enrolled = reduce(
            MyAccountScreenViewModel.Event.OnBiometricsSettingChanged(
                required = false,
                supported = true,
                available = true,
                description = null,
            )
        )(unavailable)

        assertTrue(enrolled.biometricsAvailable)
        assertEquals(null, enrolled.biometricsDescription)
    }

    @Test
    fun `no-op events return state unchanged`() {
        val state = MyAccountScreenViewModel.State(biometricsRequired = true)
        val noOpEvents = listOf(
            MyAccountScreenViewModel.Event.OnBiometricsToggled,
            MyAccountScreenViewModel.Event.OnChangeDisplayNameClicked,
            MyAccountScreenViewModel.Event.OnEditDisplayName,
            MyAccountScreenViewModel.Event.OnChangeUsernameClicked,
            MyAccountScreenViewModel.Event.OnEditUsername,
            MyAccountScreenViewModel.Event.OnProfilePictureClicked,
            MyAccountScreenViewModel.Event.OnEditProfilePicture,
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
