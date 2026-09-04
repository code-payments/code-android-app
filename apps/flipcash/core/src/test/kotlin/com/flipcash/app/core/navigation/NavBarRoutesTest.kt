package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "You" tab is the menu (settings) surface. Mirrors iOS `YouTabRoutingTests`: the TipCard tab
 * routes to the menu, and the menu route belongs to the TipCard tab — the tipping flow is now home
 * only to the Chats tab.
 */
class NavBarRoutesTest {

    @Test
    fun `the TipCard (You) tab routes to the menu`() {
        assertEquals(AppRoute.Tabs.Menu, NavBarButton.TipCard.destinationRoute())
    }

    @Test
    fun `the menu route belongs to the TipCard (You) tab`() {
        assertEquals(NavBarButton.TipCard, AppRoute.Tabs.Menu.asNavBarTab())
    }

    @Test
    fun `the Chats tab still routes through the tipping flow`() {
        assertEquals(AppRoute.Tabs.Tips(resumed = false), NavBarButton.Chats.destinationRoute())
    }

    @Test
    fun `the tipping flow maps back to the Chats tab, never TipCard`() {
        // Even the post-setup resumed form is the Chats tab now — the tip card moved to the You tab.
        assertEquals(NavBarButton.Chats, AppRoute.Tabs.Tips(resumed = true).asNavBarTab())
        assertEquals(NavBarButton.Chats, AppRoute.Tabs.Tips(resumed = false).asNavBarTab())
    }
}
