package com.flipcash.desktop.shell

import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopNavStateTest {
    private val mintA = Mint.usdf

    @Test
    fun `currency opened from wallet goes back to wallet`() {
        val s = DesktopNavState()
        s.openWallet()
        s.openCurrency(mintA)
        assertTrue(s.back())
        assertEquals(ContextContent.Wallet, s.context)
    }

    @Test
    fun `currency opened with nothing open goes back to closed`() {
        val s = DesktopNavState()
        s.openCurrency(mintA)
        assertTrue(s.back())
        assertNull(s.context)
    }

    @Test
    fun `info toggles`() {
        val s = DesktopNavState()
        s.toggleInfo()
        assertEquals(ContextContent.Info, s.context)
        s.toggleInfo()
        assertNull(s.context)
    }

    @Test
    fun `info replaces wallet rather than closing it`() {
        val s = DesktopNavState()
        s.openWallet()
        s.toggleInfo()
        assertEquals(ContextContent.Info, s.context)
    }

    @Test
    fun `escape closes the overlay but not the column`() {
        val s = DesktopNavState()
        s.openWallet()
        assertFalse(s.onEscape(ContextPaneMode.Column))
        assertEquals(ContextContent.Wallet, s.context)
        assertTrue(s.onEscape(ContextPaneMode.Overlay))
        assertNull(s.context)
    }

    @Test
    fun `escape with nothing open does nothing`() {
        val s = DesktopNavState()
        s.compactShowsChat = true
        assertFalse(s.onEscape(ContextPaneMode.Overlay))
        assertTrue(s.compactShowsChat)
    }

    @Test
    fun `token route opens currency, scanner is dropped`() {
        val s = DesktopNavState()
        s.openRoute(AppRoute.Token.Info(mintA))
        assertEquals(mintA, (s.context as ContextContent.Currency).mint)

        val t = DesktopNavState()
        t.openRoute(AppRoute.Tabs.Scanner)
        assertNull(t.context)
    }

    @Test
    fun `other routes show as unavailable`() {
        val s = DesktopNavState()
        s.openRoute(AppRoute.Token.Transactions(mintA))
        assertTrue(s.context is ContextContent.Unavailable)
    }

    @Test
    fun `back from leaderboard returns to chat`() {
        val s = DesktopNavState()
        s.main = MainContent.Leaderboard
        assertTrue(s.back())
        assertEquals(MainContent.Chat, s.main)
    }

    @Test
    fun `back from the compact chat returns to the list`() {
        val s = DesktopNavState()
        s.compactShowsChat = true
        assertTrue(s.back())
        assertFalse(s.compactShowsChat)
        assertFalse(s.back())
    }
}
