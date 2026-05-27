package com.flipcash.app.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.analytics.Action
import com.flipcash.app.analytics.Button
import com.flipcash.app.core.AppRoute
import com.flipcash.app.permissions.internal.contacts.ContactScreenContent
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberContactPermission

@Composable
fun ContactPermissionScreen(fromOnboarding: Boolean) {
    val navigator = LocalCodeNavigator.current
    val analytics = LocalAnalytics.current

    val permissionState = rememberContactPermission { result ->
        when (result) {
            PermissionResult.Granted -> {
                analytics.action(Button.AllowContacts)
                if (fromOnboarding) analytics.action(Action.CompletedOnboarding)
                navigator.push(
                    AppRoute.Onboarding.NotificationPermission(fromOnboarding)
                )
            }
            PermissionResult.Denied -> {
                navigator.push(
                    AppRoute.Onboarding.NotificationPermission(fromOnboarding)
                )
            }
            PermissionResult.PermanentlyDenied -> {
                navigator.push(
                    AppRoute.Onboarding.NotificationPermission(fromOnboarding)
                )
            }
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
    ContactScreenContent(
        permissionState = permissionState,
        onSkip = {
            analytics.action(Button.SkipContacts)
            navigator.push(
                AppRoute.Onboarding.NotificationPermission(fromOnboarding)
            )
        }
    )

    BackHandler(fromOnboarding) { }
}