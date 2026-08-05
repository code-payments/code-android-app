package com.flipcash.app.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavBarConfigTest {

    @Test
    fun `blank string yields the default config`() {
        assertEquals(NavBarConfig.Default, NavBarConfig.deserialize(""))
    }

    @Test
    fun `round-trips a full, current order`() {
        val config = NavBarConfig(
            order = listOf(NavBarButton.Wallet, NavBarButton.Give, NavBarButton.Discover, NavBarButton.Tips),
            giveButtonLabel = GiveButtonLabel.Cash,
        )
        assertEquals(config, NavBarConfig.deserialize(config.serialize()))
    }

    @Test
    fun `back-fills a button added after the order was persisted, at its default position`() {
        // A nav bar order persisted before Tips was added to the enum/defaultOrder.
        val legacy = "Discover,Give,Wallet|Cash"

        val order = NavBarConfig.deserialize(legacy).order

        assertTrue(NavBarButton.Tips in order, "Tips should be back-filled into a legacy order")
        // Inserted at its defaultOrder position (between Give and Wallet), not appended.
        assertEquals(
            listOf(NavBarButton.Discover, NavBarButton.Give, NavBarButton.Tips, NavBarButton.Wallet),
            order,
        )
    }

    @Test
    fun `back-fill preserves a user's custom ordering of existing buttons`() {
        val legacy = "Wallet,Give,Discover|Cash"

        val order = NavBarConfig.deserialize(legacy).order

        assertTrue(NavBarButton.Tips in order)
        // Existing buttons keep the user's reversed order; only the missing one is added.
        assertEquals(
            listOf(NavBarButton.Wallet, NavBarButton.Give, NavBarButton.Discover),
            order.filterNot { it == NavBarButton.Tips },
        )
    }

    @Test
    fun `unknown button names are dropped and missing known ones back-filled`() {
        val order = NavBarConfig.deserialize("Discover,Bogus,Give,Wallet|Cash").order

        assertEquals(NavBarButton.defaultOrder, order)
    }

    @Test
    fun `a persisted order containing the removed Send button is dropped without crashing`() {
        // Users who customized their nav bar before Send was removed have "Send" persisted in
        // their NavBar config. deserialize() must silently drop the now-unknown token (never
        // throw on NavBarButton.valueOf) and back-fill the current default order.
        val order = NavBarConfig.deserialize("Discover,Give,Send,Tips,Wallet|Cash").order

        assertTrue("Send" !in order.map { it.name }, "the removed Send token must not survive")
        assertEquals(NavBarButton.defaultOrder, order)
    }

    @Test
    fun `empty order falls back to the default order`() {
        val order = NavBarConfig.deserialize("|Cash").order

        assertEquals(NavBarButton.defaultOrder, order)
    }
}
