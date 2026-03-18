package com.flipcash.app.permissions

import androidx.compose.runtime.Composable
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent

@Composable
fun NotificationPermissionScreen(fromOnboarding: Boolean = false) {
    PermissionScreenContent(
        permission = Permission.Notifications,
        postCreate = fromOnboarding,
    )
}
