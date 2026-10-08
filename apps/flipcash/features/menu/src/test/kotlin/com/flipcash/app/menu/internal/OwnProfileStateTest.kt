package com.flipcash.app.menu.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
