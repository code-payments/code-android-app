package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [homeRoute] is where launch deeplinks open. Cold launch and the end of onboarding both use
 * [launchRoute] (Chats).
 */
class HomeRouteTest {

    @Test
    fun `home is the wallet tab`() {
        assertEquals(AppRoute.Tabs.Wallet, homeRoute)
    }

    @Test
    fun `home is a nav bar tab home`() {
        // Deeplinks apply it with ClearAll, so it must map to a tab or the
        // hoisted nav bar would render with no selection.
        assertEquals(NavBarButton.Wallet, homeRoute.asNavBarTab())
    }

    @Test
    fun `cold launch opens on the chats tab`() {
        assertEquals(NavBarButton.Chats, launchRoute.asNavBarTab())
    }
}
