package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * Route mapping for the hoisted v2 navigation bar (a tab bar that swaps the current top-level
 * screen). Kept here because both [NavBarButton] and [AppRoute] are core types.
 */

/** The top-level route a v2 tab switches to, or null if it has no destination yet. */
fun NavBarButton.destinationRoute(): AppRoute? = when (this) {
    NavBarButton.Scanner -> AppRoute.Main.Scanner
    NavBarButton.Wallet -> AppRoute.Sheets.Wallet
    NavBarButton.Chats -> null // TODO(v2): wire the chats destination
    NavBarButton.TipCard -> null // TODO(v2): wire the tip-card destination
    // v1-only buttons never appear in the v2 bar.
    NavBarButton.Give, NavBarButton.Discover, NavBarButton.Tips -> null
}

/** The v2 tab a top-level route belongs to, or null if the route isn't a tab home. */
fun AppRoute.asNavBarTab(): NavBarButton? = when (this) {
    AppRoute.Main.Scanner -> NavBarButton.Scanner
    AppRoute.Sheets.Wallet -> NavBarButton.Wallet
    else -> null
}
