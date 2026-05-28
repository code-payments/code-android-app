package com.flipcash.app.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.analytics.Action
import com.flipcash.app.analytics.Button
import com.flipcash.app.core.AppRoute
import com.flipcash.app.permissions.internal.notifications.NotificationRationalePermissionContent
import com.flipcash.app.permissions.internal.notifications.NotificationScreenContent
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberNotificationPermission

@Composable
fun NotificationPermissionScreen(fromOnboarding: Boolean = false) {
    val navigator = LocalCodeNavigator.current
    val analytics = LocalAnalytics.current

    val permissionState = rememberNotificationPermission { result ->
        when (result) {
            PermissionResult.Granted -> {
                analytics.action(Button.AllowPush)
                if (fromOnboarding) analytics.action(Action.CompletedOnboarding)
                navigator.navigate(
                    route = AppRoute.Main.Scanner,
                    options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll)
                )
            }
            PermissionResult.Denied -> navigator.push(
                AppRoute.Onboarding.NotificationPermissionRationale(false)
            )
            PermissionResult.PermanentlyDenied -> navigator.push(
                AppRoute.Onboarding.NotificationPermissionRationale(true)
            )
            PermissionResult.NotRequested -> Unit
        }
    }

    LaunchedEffect(Unit) {
        when (permissionState.status) {
            PermissionResult.Granted -> navigator.navigate(
                route = AppRoute.Main.Scanner,
                options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll)
            )
            PermissionResult.PermanentlyDenied -> navigator.push(
                AppRoute.Onboarding.NotificationPermissionRationale(true)
            )
            // NotRequested + Denied both render screen 1
            // Denied = show rationale (screen 1) then re-trigger dialog on OK
            PermissionResult.NotRequested,
            PermissionResult.Denied -> Unit
        }
    }

    // Only reached when status is NotRequested
    NotificationScreenContent(
        permissionState = permissionState,
        onSkip = {
            analytics.action(Button.SkipPush)
            navigator.navigate(
                route = AppRoute.Main.Scanner,
                options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll)
            )
        }
    )

    BackHandler(fromOnboarding) { }
}

@Composable
fun NotificationPermissionRationaleScreen(permanentlyDenied: Boolean = false) {
    val navigator = LocalCodeNavigator.current
    NotificationRationalePermissionContent(
        permanentlyDenied = permanentlyDenied,
        onComplete = {
            navigator.navigate(
                route = AppRoute.Main.Scanner,
                options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll)
            )
        },
    )
}
