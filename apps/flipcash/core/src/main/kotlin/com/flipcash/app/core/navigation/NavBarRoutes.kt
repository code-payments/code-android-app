package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * Route mapping for the hoisted navigation bar (a tab bar that swaps the current top-level
 * screen). Kept here because both [NavBarButton] and [AppRoute] are core types.
 */

/** The top-level route a tab switches to. */
fun NavBarButton.destinationRoute(): AppRoute = when (this) {
    NavBarButton.Scanner -> AppRoute.Main.Scanner
    NavBarButton.Wallet -> AppRoute.Sheets.Wallet
    // The chats tab routes through the tipping flow seeded at the list.
    NavBarButton.Chats -> AppRoute.Sheets.Tips(resumed = false)
    // The "You" tab is the menu (settings) surface, augmented with the tip card + share.
    NavBarButton.TipCard -> AppRoute.Sheets.Menu
}

/** The tab a top-level route belongs to, or null if the route isn't a tab home. */
fun AppRoute.asNavBarTab(): NavBarButton? = when (this) {
    AppRoute.Main.Scanner -> NavBarButton.Scanner
    AppRoute.Sheets.Wallet -> NavBarButton.Wallet
    // The tipping flow is home to the chats tab (the tip card moved to the You/menu tab).
    is AppRoute.Sheets.Tips -> NavBarButton.Chats
    AppRoute.Sheets.Menu -> NavBarButton.TipCard
    else -> null
}
