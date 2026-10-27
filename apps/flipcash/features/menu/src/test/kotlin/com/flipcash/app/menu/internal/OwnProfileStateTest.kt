package com.flipcash.app.menu.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnProfileStateTest {

    private val reduce = MenuScreenViewModel.updateStateForEvent

    @Test
    fun `the resolved fee lands in state and clears when it goes away`() {
        val withFee = reduce(MenuScreenViewModel.Event.OnMinimumToChatChanged("$1.25"))(MenuScreenViewModel.State())
        assertEquals("$1.25", withFee.minimumToChat)

        val cleared = reduce(MenuScreenViewModel.Event.OnMinimumToChatChanged(null))(withFee)
        assertNull(cleared.minimumToChat)
    }

    @Test
    fun `a named profile carries its joined label`() {
        val named = MenuScreenViewModel.ProfileState.Named(UserProfile.Empty, joined = "October 2026")
        val state = reduce(MenuScreenViewModel.Event.OnProfileStateChanged(named))(MenuScreenViewModel.State())
        assertEquals(named, state.profileState)
    }

    @Test
    fun `the share sheet opens only for a named profile`() {
        val userId = listOf<Byte>(1, 2, 3)
        val named = MenuScreenViewModel.ProfileState.Named(
            UserProfile.Empty.copy(displayName = "Ada", username = "ada"),
            joined = null,
        )
        assertEquals(
            AppRoute.Messaging.ShareProfileToChats(
                userId = userId,
                username = "ada",
                displayName = "Ada",
                directChatId = null,
            ),
            shareProfileRoute(named, userId),
        )
        assertNull(shareProfileRoute(named, userId = null))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unknown, userId))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unclaimed(placeholder = null), userId))
    }

    private val photo = MediaItem(renditions = emptyList())

    private fun profile(
        profilePicture: MediaItem? = null,
        coverPicture: MediaItem? = null,
        bio: String = "",
    ) = UserProfile.Empty.copy(profilePicture = profilePicture, coverPicture = coverPicture, bio = bio)

    @Test
    fun `a profile missing any of photo, cover or bio is incomplete`() {
        assertFalse(isProfileComplete(profile()))
        assertFalse(isProfileComplete(profile(photo, photo, bio = "")))
        assertFalse(isProfileComplete(profile(photo, photo, bio = "   ")))
        assertFalse(isProfileComplete(profile(photo, null, bio = "hi")))
        assertFalse(isProfileComplete(profile(null, photo, bio = "hi")))
        assertFalse(isProfileComplete(null))
    }

    @Test
    fun `photo, cover and bio complete the profile without a username`() {
        assertTrue(isProfileComplete(profile(photo, photo, bio = "hi").copy(username = "")))
    }

    @Test
    fun `the empty state waits for a successful read when a username is set`() {
        assertFalse(showFavoritesEmptyState("ada", favoritesReadFor = null))
        assertFalse(showFavoritesEmptyState("ada", favoritesReadFor = "old_handle"))
        assertTrue(showFavoritesEmptyState("ada", favoritesReadFor = "ada"))
    }

    @Test
    fun `the empty state draws at once when there is no username to read`() {
        assertTrue(showFavoritesEmptyState(null, favoritesReadFor = null))
        assertTrue(showFavoritesEmptyState("", favoritesReadFor = null))
    }

    @Test
    fun `a successful favorites read lands in state`() {
        val state = reduce(MenuScreenViewModel.Event.OnFavoritesRead("ada", emptyList()))(MenuScreenViewModel.State())
        assertEquals("ada", state.favoritesReadFor)
        assertEquals(emptyList(), state.featuredGroups)
    }
}
