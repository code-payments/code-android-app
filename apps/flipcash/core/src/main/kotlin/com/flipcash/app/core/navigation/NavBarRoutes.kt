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
    // Both tabs route through the tipping flow, seeded at the right step: the chats list vs the tip
    // card (resumed = true lands the flow on TipCard alone). Deeplinks/decorators use the same route.
    NavBarButton.Chats -> AppRoute.Sheets.Tips(resumed = false)
    NavBarButton.TipCard -> AppRoute.Sheets.Tips(resumed = true)
    // v1-only buttons never appear in the v2 bar.
    NavBarButton.Give, NavBarButton.Discover, NavBarButton.Tips -> null
}

/** The v2 tab a top-level route belongs to, or null if the route isn't a tab home. */
fun AppRoute.asNavBarTab(): NavBarButton? = when (this) {
    AppRoute.Main.Scanner -> NavBarButton.Scanner
    AppRoute.Sheets.Wallet -> NavBarButton.Wallet
    // The tipping flow is home to both tabs; `resumed` distinguishes the tip card from the list.
    is AppRoute.Sheets.Tips -> if (resumed) NavBarButton.TipCard else NavBarButton.Chats
    else -> null
}
