package com.flipcash.app.menu.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.UserProfile
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

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
    fun `joined is the full month and year`() {
        // 2026-10-06T12:00:00Z
        val joinedAt = Instant.fromEpochSeconds(1_791_288_000)
        assertEquals("October 2026", joinedLabel(joinedAt, Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun `joined follows the locale`() {
        val joinedAt = Instant.fromEpochSeconds(1_791_288_000)
        assertEquals("octubre 2026", joinedLabel(joinedAt, Locale.forLanguageTag("es"), ZoneOffset.UTC))
    }

    @Test
    fun `no join date means no label`() {
        assertNull(joinedLabel(null, Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun `the share sheet opens only for a named profile`() {
        val named = MenuScreenViewModel.ProfileState.Named(UserProfile.Empty, joined = null)
        assertEquals(AppRoute.Menu.ShareProfile, shareProfileRoute(named))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unknown))
        assertNull(shareProfileRoute(MenuScreenViewModel.ProfileState.Unclaimed(placeholder = null)))
    }
}
