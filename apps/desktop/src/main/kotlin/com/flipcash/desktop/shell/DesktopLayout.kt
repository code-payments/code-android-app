package com.flipcash.desktop.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton

/** The window's width class (Decisions node 12631:459, card 1): Material's window size classes. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassFor(width: Dp): WidthClass = when {
    width < 600.dp -> WidthClass.Compact
    width < 840.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** The mobile tabs minus Scan, which needs a camera: Chat, Money, You. Desktop opens to Chat. */
val DesktopTabs = listOf(NavBarButton.Chats, NavBarButton.Wallet, NavBarButton.TipCard)
val DesktopStartTab = NavBarButton.Chats

/** The sizes the Figma frames set (Chats 12627:208, Money 12626:56). */
object DesktopDimens {
    val MinWindowWidth = 400.dp
    val MinWindowHeight = 640.dp

    val RailWidth = 64.dp

    val ChatListMedium = 280.dp
    val ChatListExpanded = 360.dp
    val MessagesMaxWidth = 720.dp

    val MoneySinglePaneMaxWidth = 560.dp
    val MoneyListExpanded = 400.dp
    val DetailMaxWidth = 720.dp
}
