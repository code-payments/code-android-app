package com.flipcash.app.myaccount.internal.editprofile

import com.flipcash.services.models.UserProfile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditProfileViewModelTest {

    private val reduce = EditProfileViewModel.updateStateForEvent

    private fun profile(
        name: String = "Brandon",
        username: String? = null,
        autoAssigned: Boolean = false,
        bio: String = "",
    ) = UserProfile.Empty.copy(
        displayName = name,
        username = username,
        isUsernameAutoAssigned = autoAssigned,
        bio = bio,
    )

    private fun EditProfileViewModel.State.after(profile: UserProfile) =
        reduce(EditProfileViewModel.Event.OnProfileChanged(profile))(this)

    @Test
    fun `a profile with no username still needs the claim`() {
        assertTrue(EditProfileViewModel.State().after(profile(username = null)).usernameNeedsClaim)
        assertTrue(EditProfileViewModel.State().after(profile(username = "")).usernameNeedsClaim)
    }

    @Test
    fun `a server-assigned username still needs the claim`() {
        val state = EditProfileViewModel.State().after(profile(username = "brandon123", autoAssigned = true))
        assertTrue(state.usernameNeedsClaim)
    }

    @Test
    fun `a chosen username does not need the claim and is carried for display`() {
        val state = EditProfileViewModel.State().after(profile(username = "mcansh"))
        assertFalse(state.usernameNeedsClaim)
        assertEquals("mcansh", state.username)
    }

    @Test
    fun `name and bio follow the profile, including being cleared`() {
        val filled = EditProfileViewModel.State().after(profile(name = "Brandon", bio = "hello"))
        assertEquals("Brandon", filled.displayName)
        assertEquals("hello", filled.bio)

        val cleared = filled.after(profile(name = "", bio = ""))
        assertEquals("", cleared.displayName)
        assertEquals("", cleared.bio)
    }

    @Test
    fun `minimum to chat is unknown until it resolves`() {
        assertNull(EditProfileViewModel.State().minimumToChat)
        val state = reduce(EditProfileViewModel.Event.OnMinimumToChatChanged("$1.00"))(EditProfileViewModel.State())
        assertEquals("$1.00", state.minimumToChat)
    }
}
