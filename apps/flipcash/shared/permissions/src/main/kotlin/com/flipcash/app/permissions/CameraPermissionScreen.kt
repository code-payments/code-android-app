package com.flipcash.app.permissions

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.permissions.internal.Permission
import com.flipcash.app.permissions.internal.PermissionScreenContent

class CameraPermissionScreen(private val fromOnboarding: Boolean = false): Screen {

    @Composable
    override fun Content() {
        PermissionScreenContent(
            permission = Permission.Camera,
            postCreate = fromOnboarding,
        )
    }
}