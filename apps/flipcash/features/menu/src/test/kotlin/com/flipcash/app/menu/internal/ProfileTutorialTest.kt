package com.flipcash.app.menu.internal

import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTutorialTest {

    // MediaItem is a plain data class over a rendition list, so an empty one stands in for
    // "a picture is set" without needing a mocking library in this module.
    private val anyPicture = MediaItem(renditions = emptyList())

    private fun profile(
        name: String = "Brandon",
        picture: MediaItem? = null,
        minimumTip: Fiat? = null,
    ) = UserProfile(
        displayName = name,
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
    fun `the steps run display name, picture, minimum tip`() {
        val items = profileTutorialItems(profile(name = ""))
        assertEquals(
            listOf(
                TutorialItem.DisplayName::class,
                TutorialItem.ProfilePicture::class,
                TutorialItem.MinimumTip::class,
            ),
            items?.map { it::class },
        )
    }

    @Test
    fun `a nameless profile leaves every step outstanding`() {
        val items = profileTutorialItems(profile(name = ""))
        assertTrue(items!!.none { it.isCompleted })
    }

    @Test
    fun `a named profile shows the display name step ticked rather than dropping it`() {
        val items = profileTutorialItems(profile())
        assertEquals(3, items?.size)
        assertEquals(1, items!!.count { it.isCompleted })
        assertTrue(items.first { it is TutorialItem.DisplayName }.isCompleted)
    }

    @Test
    fun `a picture completes the picture step`() {
        val items = profileTutorialItems(profile(picture = anyPicture))
        assertTrue(items!!.first { it is TutorialItem.ProfilePicture }.isCompleted)
        assertFalse(items.first { it is TutorialItem.MinimumTip }.isCompleted)
    }

    @Test
    fun `a saved minimum tip completes the minimum tip step`() {
        val items = profileTutorialItems(profile(minimumTip = Fiat(1.0)))
        assertTrue(items!!.first { it is TutorialItem.MinimumTip }.isCompleted)
        assertFalse(items.first { it is TutorialItem.ProfilePicture }.isCompleted)
    }

    @Test
    fun `a name, a picture and a minimum tip take the checklist away entirely`() {
        assertNull(profileTutorialItems(profile(picture = anyPicture, minimumTip = Fiat(1.0))))
    }

    @Test
    fun `a nameless profile keeps the checklist even with the other two steps done`() {
        val items = profileTutorialItems(
            profile(name = "", picture = anyPicture, minimumTip = Fiat(1.0))
        )
        assertEquals(listOf(false, true, true), items?.map { it.isCompleted })
    }
}
