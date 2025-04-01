package com.getcode.util.permissions

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.getcode.util.getActivity

enum class PermissionResult {
    Granted, Denied, ShouldShowRationale
}

typealias PermissionsLauncher = ManagedActivityResultLauncher<String, Boolean>

@Composable
fun getPermissionLauncher(
    permission: String,
    onPermissionResult: (result: PermissionResult) -> Unit
): PermissionsLauncher {
    val context = LocalContext.current
    val activity = context as Activity

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // This block will be triggered after the user chooses to grant or deny the permission
        // and we can tell if the user permanently declines or if we need to show rational
        val permissionPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, permission
        ) && !isGranted

        when {
            permissionPermanentlyDenied -> {
                onPermissionResult(PermissionResult.ShouldShowRationale)
            }
            !isGranted -> onPermissionResult(PermissionResult.Denied)
            else -> onPermissionResult(PermissionResult.Granted)
        }
    }

    return launcher
}

@Composable
fun rememberPermissionHandler(): PermissionHandler{
    val context = LocalContext.current
    return remember(context) {
        PermissionHandler(context)
    }
}

class PermissionHandler(private val context: Context) {
    fun request(
        permission: String,
        shouldRequest: Boolean = true,
        launcher: PermissionsLauncher,
        onPermissionResult: (result: PermissionResult) -> Unit = { },
    ) {
        val activity = context.getActivity()

        when (ContextCompat.checkSelfPermission(context, permission)) {
            PackageManager.PERMISSION_GRANTED -> {
                onPermissionResult(PermissionResult.Granted)
            }
            PackageManager.PERMISSION_DENIED -> {
                if (shouldRequest) {
                    launcher.launch(permission)
                } else {
                    if (activity != null) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                            onPermissionResult(PermissionResult.ShouldShowRationale)
                            return
                        }
                    }
                    onPermissionResult(PermissionResult.Denied)
                }
            }
        }
    }
}