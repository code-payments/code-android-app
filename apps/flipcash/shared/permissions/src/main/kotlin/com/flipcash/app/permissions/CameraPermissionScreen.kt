package com.flipcash.app.permissions

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent
import com.getcode.navigation.screens.AppScreen

class CameraPermissionScreen(private val fromOnboarding: Boolean = false): AppScreen {
    override val testTag: String = "camera_permission_screen"

    @Composable
    override fun ScreenContent() {
        PermissionScreenContent(
            permission = Permission.Camera,
            postCreate = fromOnboarding,
        )
    }
}