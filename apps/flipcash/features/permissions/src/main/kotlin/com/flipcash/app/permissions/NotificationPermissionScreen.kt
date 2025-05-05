package com.flipcash.app.permissions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.permissions.internal.NotificationScreenContent
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent
import com.getcode.theme.DesignSystem

class NotificationPermissionScreen(private val fromOnboarding: Boolean = false): Screen {

    @Composable
    override fun Content() {
        PermissionScreenContent(
            permission = Permission.Notifications,
            fromOnboarding = fromOnboarding,
        )
    }
}

@Composable
@Preview(showSystemUi = true, showBackground = true)
private fun PreviewNotificationPermissionScreen() {
    DesignSystem {
        NotificationScreenContent {  }
    }
}