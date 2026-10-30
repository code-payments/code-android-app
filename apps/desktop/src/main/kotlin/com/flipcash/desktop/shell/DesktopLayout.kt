package com.flipcash.desktop.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton

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

/** How the context pane (Info, Wallet, Currency, You) sits next to the main pane. */
enum class ContextPaneMode { Column, Overlay }

fun contextPaneModeFor(widthClass: WidthClass): ContextPaneMode =
    if (widthClass == WidthClass.Wide) ContextPaneMode.Column else ContextPaneMode.Overlay

/** The mobile tabs minus Scan, which needs a camera: Chat, Money, You. Desktop opens to Chat. */
val DesktopTabs = listOf(NavBarButton.Chats, NavBarButton.Wallet, NavBarButton.TipCard)
val DesktopStartTab = NavBarButton.Chats

/** The sizes the Figma frames set (window 13000:91, widths 13045:3284). */
object DesktopDimens {
    val MinWindowWidth = 400.dp
    val MinWindowHeight = 640.dp

    val RailWidth = 64.dp

    val SidebarMedium = 280.dp
    val SidebarExpanded = 300.dp
    val SidebarGlassInset = 8.dp
    val ContextPaneWidth = 336.dp
    val MessagesMaxWidth = 720.dp

    val MoneySinglePaneMaxWidth = 560.dp
    val MoneyListExpanded = 400.dp
    val DetailMaxWidth = 720.dp
}
