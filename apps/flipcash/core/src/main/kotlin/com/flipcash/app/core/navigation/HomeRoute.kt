package com.flipcash.app.core.navigation

import com.flipcash.app.core.AppRoute

/**
 * The top-level route the app releases to once there's a session — on launch and when onboarding
 * finishes.
 *
 * The wallet is a flat tab home (not a sheet), so this route is applied with `replaceAll` /
 * [com.getcode.navigation.core.NavOptions.PopUpTo.ClearAll], exactly like a tab switch from the
 * nav bar.
 */
val homeRoute: AppRoute = AppRoute.Sheets.Wallet
