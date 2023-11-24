package com.getcode.view.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

@Composable
fun getPermissionLauncher(onPermissionResult: (isGranted: Boolean) -> Unit) =
    rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        onPermissionResult(isGranted)
    }

object PermissionCheck {
    fun requestPermission(
        context: Context,
        permission: String,
        shouldRequest: Boolean,
        onPermissionResult: (isGranted: Boolean) -> Unit,
        launcher: ManagedActivityResultLauncher<String, Boolean>
    ) {
        when (ContextCompat.checkSelfPermission(context, permission)) {
            PackageManager.PERMISSION_GRANTED -> {
                onPermissionResult(true)
            }
            PackageManager.PERMISSION_DENIED -> {
                if (shouldRequest) {
                    launcher.launch(permission)
                } else {
                    onPermissionResult(false)
                }
            }
        }
    }
}