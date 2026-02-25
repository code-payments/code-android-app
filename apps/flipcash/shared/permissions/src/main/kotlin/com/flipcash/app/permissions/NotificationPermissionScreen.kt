package com.flipcash.app.permissions

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent
import com.getcode.navigation.screens.AppScreen

class NotificationPermissionScreen(private val fromOnboarding: Boolean = false): AppScreen {

    override val testTag: String = "notification_permission_screen"

    @Composable
    override fun ScreenContent() {
        PermissionScreenContent(
            permission = Permission.Notifications,
            postCreate = fromOnboarding,
        )
    }
}