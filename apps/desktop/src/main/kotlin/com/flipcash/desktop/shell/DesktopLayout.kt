package com.flipcash.desktop.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window's width class (widths node 13045:3284). Material's classes, plus Wide at 1100, where the
 * context pane gets its own column instead of sliding over the main pane.
 */
enum class WidthClass { Compact, Medium, Expanded, Wide }

fun widthClassFor(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    width < 1100.dp -> WidthClass.Expanded
    else -> WidthClass.Wide
}

/**
 * How the chat list sits at the window's leading edge (widths node 13045:3284). Hidden is the user's
 * choice and wins at every width; Rail is the list folding by itself so an open pane gets a column.
 */
enum class SidebarMode { List, Rail, Hidden }

fun sidebarModeFor(widthClass: WidthClass, contextOpen: Boolean, hidden: Boolean): SidebarMode = when {
    hidden -> SidebarMode.Hidden
    widthClass == WidthClass.Expanded && contextOpen -> SidebarMode.Rail
    else -> SidebarMode.List
}

/** How the context pane (Info, Wallet, Currency, You) sits next to the main pane. */
enum class ContextPaneMode { Column, Overlay }

/** A column at Wide, and at Expanded once the list is out of the way; below that it floats over the chat. */
fun contextPaneModeFor(widthClass: WidthClass, sidebar: SidebarMode): ContextPaneMode = when (widthClass) {
    WidthClass.Wide -> ContextPaneMode.Column
    WidthClass.Expanded -> if (sidebar == SidebarMode.List) ContextPaneMode.Overlay else ContextPaneMode.Column
    WidthClass.Medium, WidthClass.Compact -> ContextPaneMode.Overlay
}

/** The sizes the Figma frames set (window 13000:91, widths 13045:3284). */
object DesktopDimens {
    val MinWindowWidth = 400.dp
    val MinWindowHeight = 640.dp

    val SidebarMedium = 280.dp
    val SidebarExpanded = 300.dp
    val SidebarRail = 80.dp
    val SidebarGlassInset = 8.dp

    /** The row the traffic lights sit in: the top of the sidebar and the chat's toolbar. */
    val TitleRowHeight = 52.dp
    val ContextPaneWidth = 336.dp
    val MessagesMaxWidth = 720.dp
}
