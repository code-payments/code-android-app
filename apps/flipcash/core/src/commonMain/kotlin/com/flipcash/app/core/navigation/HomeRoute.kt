package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * The base route every resolved launch deeplink opens on.
 *
 * The wallet is a flat tab home (not a sheet), so this route is applied with `replaceAll` /
 * [com.getcode.navigation.core.NavOptions.PopUpTo.ClearAll], exactly like a tab switch from the
 * nav bar.
 */
val homeRoute: AppRoute = AppRoute.Tabs.Wallet

/**
 * The tab a cold launch opens on when no deeplink directs it elsewhere: Chats. Built from the tab's
 * own destination so launching lands on the same route a tap on the Chats tab does.
 */
val launchRoute: AppRoute = NavBarButton.Chats.destinationRoute()
