package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * Route mapping for the hoisted navigation bar (a tab bar that swaps the current top-level
 * screen). Kept here because both [NavBarButton] and [AppRoute] are core types.
 */

/** The top-level route a tab switches to. */
fun NavBarButton.destinationRoute(): AppRoute.Tabs = when (this) {
    NavBarButton.Scanner -> AppRoute.Tabs.Scanner
    NavBarButton.Wallet -> AppRoute.Tabs.Wallet
    // The chats tab routes through the tipping flow seeded at the list.
    NavBarButton.Chats -> AppRoute.Tabs.Tips(resumed = false)
    // The "You" tab is the menu (settings) surface, augmented with the tip card + share.
    NavBarButton.TipCard -> AppRoute.Tabs.Menu
}

/**
 * The tab a tab home belongs to. Exhaustive over [AppRoute.Tabs], so a new tab home fails to
 * compile until it is given a button rather than silently behaving as an ordinary push.
 */
fun AppRoute.Tabs.navBarButton(): NavBarButton = when (this) {
    AppRoute.Tabs.Scanner -> NavBarButton.Scanner
    AppRoute.Tabs.Wallet -> NavBarButton.Wallet
    is AppRoute.Tabs.Tips -> NavBarButton.Chats
    AppRoute.Tabs.Menu -> NavBarButton.TipCard
}

/** The tab a top-level route belongs to, or null if the route isn't a tab home. */
fun AppRoute.asNavBarTab(): NavBarButton? = (this as? AppRoute.Tabs)?.navBarButton()
