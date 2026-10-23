package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * Route mapping for the hoisted navigation bar (a tab bar that swaps the current top-level
 * screen). Kept here because both [NavBarButton] and [AppRoute] are core types.
 */

/** The top-level route a tab switches to. */
fun NavBarButton.destinationRoute(): AppRoute = when (this) {
    NavBarButton.Scanner -> AppRoute.Tabs.Scanner
    NavBarButton.Wallet -> AppRoute.Tabs.Wallet
    NavBarButton.Chats -> AppRoute.Tabs.Chats
    // The "You" tab is the menu (settings) surface, augmented with the tip card + share.
    NavBarButton.TipCard -> AppRoute.Tabs.Menu
}

/** The tab a top-level route belongs to, or null if the route isn't a tab home. */
fun AppRoute.asNavBarTab(): NavBarButton? = when (this) {
    AppRoute.Tabs.Scanner -> NavBarButton.Scanner
    AppRoute.Tabs.Wallet -> NavBarButton.Wallet
    AppRoute.Tabs.Chats -> NavBarButton.Chats
    AppRoute.Tabs.Menu -> NavBarButton.TipCard
    else -> null
}
