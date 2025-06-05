package com.flipcash.app.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.permissions.internal.NotificationScreenContent
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.theme.CodeTheme
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
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            NotificationScreenContent { }
        }
    }
}