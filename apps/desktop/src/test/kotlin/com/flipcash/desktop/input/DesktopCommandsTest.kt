package com.flipcash.desktop.input

import androidx.compose.ui.input.key.Key
import com.flipcash.desktop.shell.ContextContent
import com.flipcash.desktop.shell.ContextPaneMode
import com.flipcash.desktop.shell.DesktopNavState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCommandsTest {
    private fun mac(key: Key) = commandFor(key, meta = true, ctrl = false, shift = false, os = HostOs.Mac)

    @Test
    fun `mac uses command`() {
        assertEquals(DesktopCommand.Search, mac(Key.K))
        assertNull(commandFor(Key.K, meta = false, ctrl = true, shift = false, os = HostOs.Mac))
    }

    @Test
    fun `windows and linux use control`() {
        for (os in listOf(HostOs.Windows, HostOs.Linux)) {
            assertEquals(DesktopCommand.ToggleInfo, commandFor(Key.I, meta = false, ctrl = true, shift = false, os = os))
            assertNull(commandFor(Key.I, meta = true, ctrl = false, shift = false, os = os))
        }
    }

    @Test
    fun `the spec's table`() {
        assertEquals(DesktopCommand.Search, mac(Key.K))
        assertEquals(DesktopCommand.NewChat, mac(Key.N))
        assertEquals(DesktopCommand.ToggleInfo, mac(Key.I))
        assertEquals(DesktopCommand.Back, mac(Key.LeftBracket))
        assertEquals(DesktopCommand.Settings, mac(Key.Comma))
        assertEquals(DesktopCommand.OpenInNewWindow, mac(Key.Enter))
        assertEquals(DesktopCommand.Escape, commandFor(Key.Escape, false, false, false, HostOs.Mac))
    }

    @Test
    fun `plain letters are not commands`() {
        assertNull(commandFor(Key.K, meta = false, ctrl = false, shift = false, os = HostOs.Mac))
    }

    @Test
    fun `shifted shortcuts are not these commands`() {
        assertNull(commandFor(Key.K, meta = true, ctrl = false, shift = true, os = HostOs.Mac))
    }

    @Test
    fun `alt and altgr are never commands`() {
        // AltGr arrives as Ctrl+Alt on Windows and types characters on many layouts.
        assertNull(commandFor(Key.N, meta = false, ctrl = true, shift = false, os = HostOs.Windows, alt = true))
        assertNull(commandFor(Key.I, meta = true, ctrl = false, shift = false, os = HostOs.Mac, alt = true))
        assertNull(commandFor(Key.Escape, meta = false, ctrl = false, shift = false, os = HostOs.Mac, alt = true))
    }

    @Test
    fun `toggle info opens and closes info`() {
        val nav = DesktopNavState()
        assertTrue(nav.perform(DesktopCommand.ToggleInfo, ContextPaneMode.Column))
        assertEquals(ContextContent.Info, nav.context)
        nav.perform(DesktopCommand.ToggleInfo, ContextPaneMode.Column)
        assertNull(nav.context)
    }

    @Test
    fun `escape closes the overlay but not the column`() {
        val nav = DesktopNavState().apply { openWallet() }
        assertFalse(nav.perform(DesktopCommand.Escape, ContextPaneMode.Column))
        assertEquals(ContextContent.Wallet, nav.context)
        assertTrue(nav.perform(DesktopCommand.Escape, ContextPaneMode.Overlay))
        assertNull(nav.context)
    }

    @Test
    fun `commands without a screen yet are not consumed`() {
        val nav = DesktopNavState()
        for (c in listOf(DesktopCommand.Search, DesktopCommand.NewChat, DesktopCommand.OpenInNewWindow, DesktopCommand.Settings)) {
            assertFalse(nav.perform(c, ContextPaneMode.Column))
        }
    }

    @Test
    fun `hide sidebar is control command S on mac and control shift S elsewhere`() {
        assertEquals(DesktopCommand.ToggleSidebar, commandFor(Key.S, meta = true, ctrl = true, shift = false, os = HostOs.Mac))
        assertNull(commandFor(Key.S, meta = true, ctrl = false, shift = false, os = HostOs.Mac))
        assertNull(commandFor(Key.S, meta = true, ctrl = true, shift = true, os = HostOs.Mac))
        for (os in listOf(HostOs.Windows, HostOs.Linux)) {
            assertEquals(DesktopCommand.ToggleSidebar, commandFor(Key.S, meta = false, ctrl = true, shift = true, os = os))
            assertNull(commandFor(Key.S, meta = false, ctrl = true, shift = false, os = os))
            assertNull(commandFor(Key.S, meta = false, ctrl = true, shift = true, os = os, alt = true))
        }
    }

    @Test
    fun `toggle sidebar hides and shows it`() {
        val nav = DesktopNavState()
        assertTrue(nav.perform(DesktopCommand.ToggleSidebar, ContextPaneMode.Column))
        assertTrue(nav.sidebarHidden)
        nav.perform(DesktopCommand.ToggleSidebar, ContextPaneMode.Column)
        assertFalse(nav.sidebarHidden)
    }
}
