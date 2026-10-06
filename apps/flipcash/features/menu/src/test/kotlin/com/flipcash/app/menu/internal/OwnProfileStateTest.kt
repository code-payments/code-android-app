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
        val named = MenuScreenViewModel.ProfileState.Named(UserProfile.Empty, joined = null)
        assertEquals(AppRoute.Menu.ShareProfile, shareProfileRoute(named))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unknown))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unclaimed(placeholder = null)))
    }
}
