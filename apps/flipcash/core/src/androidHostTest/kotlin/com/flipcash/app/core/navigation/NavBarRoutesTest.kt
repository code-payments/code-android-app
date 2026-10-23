package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "You" tab is the menu (settings) surface. Mirrors iOS `YouTabRoutingTests`: the TipCard tab
 * routes to the menu, and the menu route belongs to the TipCard tab.
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
    fun `the Chats tab routes to the chat list`() {
        assertEquals(AppRoute.Tabs.Chats, NavBarButton.Chats.destinationRoute())
    }

    @Test
    fun `the chat list maps back to the Chats tab, never TipCard`() {
        assertEquals(NavBarButton.Chats, AppRoute.Tabs.Chats.asNavBarTab())
    }
}
