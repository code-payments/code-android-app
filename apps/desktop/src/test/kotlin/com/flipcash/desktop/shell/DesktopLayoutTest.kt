package com.flipcash.desktop.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLayoutTest {
    @Test
    fun `width classes switch at 600, 840 and 1100`() {
        assertEquals(WidthClass.Compact, widthClassFor(400.dp))
        assertEquals(WidthClass.Compact, widthClassFor(599.dp))
        assertEquals(WidthClass.Medium, widthClassFor(600.dp))
        assertEquals(WidthClass.Medium, widthClassFor(839.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(840.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(1099.dp))
        assertEquals(WidthClass.Wide, widthClassFor(1100.dp))
    }

    @Test
    fun `the list folds to a rail only at expanded with a pane open`() {
        assertEquals(SidebarMode.Rail, sidebarModeFor(WidthClass.Expanded, contextOpen = true, hidden = false))
        assertEquals(SidebarMode.List, sidebarModeFor(WidthClass.Expanded, contextOpen = false, hidden = false))
        for (w in listOf(WidthClass.Compact, WidthClass.Medium, WidthClass.Wide)) {
            assertEquals(SidebarMode.List, sidebarModeFor(w, contextOpen = true, hidden = false), "at $w")
        }
    }

    @Test
    fun `hiding the sidebar wins over the rail`() {
        for (w in WidthClass.entries) {
            assertEquals(SidebarMode.Hidden, sidebarModeFor(w, contextOpen = true, hidden = true), "at $w")
        }
    }

    @Test
    fun `context pane is a column at wide, or at expanded once the list is out of the way`() {
        for (s in SidebarMode.entries) {
            assertEquals(ContextPaneMode.Column, contextPaneModeFor(WidthClass.Wide, s))
            assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Medium, s))
            assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Compact, s))
        }
        assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Expanded, SidebarMode.List))
        assertEquals(ContextPaneMode.Column, contextPaneModeFor(WidthClass.Expanded, SidebarMode.Rail))
        assertEquals(ContextPaneMode.Column, contextPaneModeFor(WidthClass.Expanded, SidebarMode.Hidden))
    }
}
