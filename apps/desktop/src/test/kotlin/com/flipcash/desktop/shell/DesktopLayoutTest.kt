package com.flipcash.desktop.shell

import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLayoutTest {
    @Test
    fun `width classes switch at 600 and 840`() {
        assertEquals(WidthClass.Compact, widthClassFor(400.dp))
        assertEquals(WidthClass.Compact, widthClassFor(599.dp))
        assertEquals(WidthClass.Medium, widthClassFor(600.dp))
        assertEquals(WidthClass.Medium, widthClassFor(839.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(840.dp))
    }

    @Test
    fun `desktop has no scan tab and opens to chat`() {
        assertEquals(listOf(NavBarButton.Chats, NavBarButton.Wallet, NavBarButton.TipCard), DesktopTabs)
        assertEquals(NavBarButton.Chats, DesktopStartTab)
    }
}
