package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Home is where both launch and the end of onboarding release the user to — the Wallet tab, not the
 * camera — so a freshly onboarded user lands on their balance.
 */
class HomeRouteTest {

    @Test
    fun `home is the wallet tab`() {
        assertEquals(AppRoute.Sheets.Wallet, homeRoute)
    }

    @Test
    fun `home is a nav bar tab home`() {
        // Guards the release path: it's applied with ClearAll, so it must map to a tab or the
        // hoisted nav bar would render with no selection.
        assertEquals(NavBarButton.Wallet, homeRoute.asNavBarTab())
    }
}
