package com.getcode.view.login

import android.Manifest
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.ui.components.PermissionCheck
import com.getcode.ui.components.getPermissionLauncher
import com.getcode.util.launchAppSettings

@Composable
fun notificationPermissionCheck(isShowError: Boolean = true, onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    val onPermissionError = {
        TopBarManager.showMessage(
            TopBarManager.TopBarMessage(
                title = context.getString(R.string.action_allowPushNotifications),
                message = context.getString(R.string.permissions_description_push),
                type = TopBarManager.TopBarMessageType.ERROR,
                secondaryText = context.getString(R.string.action_openSettings),
                secondaryAction = { context.launchAppSettings() }
            )
        )
    }
    val onPermissionResult = { isGranted: Boolean ->
        onResult(isGranted)
        if (!isGranted && permissionRequested && isShowError) {
            onPermissionError()
        }
        Unit
    }
    val launcher = getPermissionLauncher(onPermissionResult)
    val permissionCheck = { shouldRequest: Boolean ->
        if (Build.VERSION.SDK_INT < 33) {
            onPermissionResult(true)
        } else {
            permissionRequested = shouldRequest
            PermissionCheck.requestPermission(
                context = context,
                permission = Manifest.permission.POST_NOTIFICATIONS,
                shouldRequest = shouldRequest,
                onPermissionResult = onPermissionResult,
                launcher = launcher
            )
        }


    }

    return permissionCheck
}