package com.flipcash.app.menu.internal

import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTutorialTest {

    // MediaItem is a plain data class over a rendition list, so an empty one stands in for
    // "a picture is set" without needing a mocking library in this module.
    private val anyPicture = MediaItem(renditions = emptyList())

    private fun profile(picture: MediaItem? = null, minimumTip: Fiat? = null) = UserProfile(
        displayName = "Brandon",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        profilePicture = picture,
        minDmChatInitFee = minimumTip,
    )

    @Test
    fun `an unresolved profile has no checklist`() {
        assertNull(profileTutorialItems(profile = null))
    }

    @Test
    fun `a bare profile leaves both steps outstanding`() {
        val items = profileTutorialItems(profile())
        assertEquals(2, items?.size)
        assertTrue(items!!.none { it.isCompleted })
    }

    @Test
    fun `a profile with a picture completes only the picture step`() {
        val items = profileTutorialItems(profile(picture = anyPicture))
        assertEquals(1, items?.count { it.isCompleted })
        assertTrue(items!!.first { it is TutorialItem.ProfilePicture }.isCompleted)
    }

    @Test
    fun `a saved minimum tip completes only the minimum tip step`() {
        val items = profileTutorialItems(profile(minimumTip = Fiat(1.0)))
        assertEquals(1, items?.count { it.isCompleted })
        assertTrue(items!!.first { it is TutorialItem.MinimumTip }.isCompleted)
    }

    @Test
    fun `a picture and a minimum tip take the checklist away entirely`() {
        assertNull(profileTutorialItems(profile(picture = anyPicture, minimumTip = Fiat(1.0))))
    }
}
