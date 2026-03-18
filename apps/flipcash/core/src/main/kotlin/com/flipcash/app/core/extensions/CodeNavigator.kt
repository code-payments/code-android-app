package com.flipcash.app.core.extensions

import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.NavOptions

/**
 * Navigate to a route, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet]
 * so the [ModalBottomSheetSceneStrategy] renders them in a bottom sheet.
 */
fun CodeNavigator.navigateTo(route: NavKey, options: NavOptions = NavOptions()) {
    val destination = if (route is AppRoute.Sheets) {
        AppRoute.Main.Sheet(route)
    } else {
        route
    }
    navigate(destination, options)
}

/**
 * Navigate to multiple routes, wrapping [AppRoute.Sheets] in [AppRoute.Main.Sheet].
 * The first route uses the provided [options], subsequent routes use default options.
 */
fun CodeNavigator.navigateTo(routes: List<NavKey>, options: NavOptions = NavOptions()) {
    if (routes.isEmpty()) return
    routes.forEachIndexed { index, route ->
        navigateTo(route, if (index == 0) options else NavOptions())
    }
}
