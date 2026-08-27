package com.flipcash.app.menu.internal

import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTutorialTest {

    // MediaItem is a plain data class over a rendition list, so an empty one stands in for
    // "a picture is set" without needing a mocking library in this module.
    private val anyPicture = MediaItem(renditions = emptyList())

    private fun profile(picture: MediaItem?) = UserProfile(
        displayName = "Brandon",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        profilePicture = picture,
    )

    @Test
    fun `an unresolved profile has no checklist`() {
        assertNull(profileTutorialItems(profile = null))
    }

    @Test
    fun `a profile without a picture leaves both steps outstanding`() {
        val items = profileTutorialItems(profile(picture = null))
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
    fun `the minimum tip step never completes`() {
        val items = profileTutorialItems(profile(picture = anyPicture))
        assertTrue(items!!.none { it is TutorialItem.MinimumTip && it.isCompleted })
    }
}
