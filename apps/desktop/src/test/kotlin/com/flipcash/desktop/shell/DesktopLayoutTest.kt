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
    fun `context pane overlays below wide`() {
        assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Compact))
        assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Medium))
        assertEquals(ContextPaneMode.Overlay, contextPaneModeFor(WidthClass.Expanded))
        assertEquals(ContextPaneMode.Column, contextPaneModeFor(WidthClass.Wide))
    }
}
