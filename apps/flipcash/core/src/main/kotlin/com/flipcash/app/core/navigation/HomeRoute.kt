package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * The top-level route the app releases to when onboarding finishes creating a new account, and
 * under a launch deeplink (whose token and cash-link actions open on the wallet).
 *
 * The wallet is a flat tab home (not a sheet), so this route is applied with `replaceAll` /
 * [com.getcode.navigation.core.NavOptions.PopUpTo.ClearAll], exactly like a tab switch from the
 * nav bar.
 */
val homeRoute: AppRoute = AppRoute.Sheets.Wallet

/**
 * The tab a cold launch opens on when no deeplink directs it elsewhere: Chats. Built from the tab's
 * own destination so launching lands on the same route a tap on the Chats tab does.
 */
val launchRoute: AppRoute = NavBarButton.Chats.destinationRoute()
