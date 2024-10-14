package com.getcode.util.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.getcode.libs.permissions.R
import com.getcode.manager.TopBarManager

@Composable
fun notificationPermissionCheck(isShowError: Boolean = true, onResult: (Boolean) -> Unit): (shouldRequest: Boolean) -> Unit {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        notificationPermissionCheckApi33(isShowError, onResult)
    } else {
        notificationPermissionCheckApiLegacy(isShowError, onResult)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun notificationPermissionCheckApi33(
    isShowError: Boolean = true,
    onResult: (Boolean) -> Unit
): (shouldRequest: Boolean) -> Unit {
    val context = LocalContext.current
    val permissionChecker = rememberPermissionHandler()
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
    val onPermissionResult = { result: PermissionResult ->
        val isGranted = result == PermissionResult.Granted
        onResult(isGranted)
        if (!isGranted && permissionRequested && isShowError) {
            onPermissionError()
        }
        Unit
    }

    val launcher = getPermissionLauncher(
        Manifest.permission.POST_NOTIFICATIONS, onPermissionResult
    )

    val permissionCheck = { shouldRequest: Boolean ->
        if (Build.VERSION.SDK_INT < 33) {
            onPermissionResult(PermissionResult.Granted)
        } else {
            permissionRequested = shouldRequest
            permissionChecker.request(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                shouldRequest = shouldRequest,
                onPermissionResult = onPermissionResult,
                launcher = launcher
            )
        }
    }

    return permissionCheck
}

@Composable
private fun notificationPermissionCheckApiLegacy(
    isShowError: Boolean = true,
    onResult: (Boolean) -> Unit
): (shouldRequest: Boolean) -> Unit {
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
    val onPermissionResult = { result: PermissionResult ->
        val isGranted = result == PermissionResult.Granted
        onResult(isGranted)
        if (!isGranted && permissionRequested && isShowError) {
            onPermissionError()
        }
        Unit
    }

    val permissionCheck = { _: Boolean ->
        onPermissionResult(PermissionResult.Granted)
    }

    return permissionCheck
}

internal fun Context.appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", packageName, null)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
}

internal fun Context.launchAppSettings() {
    val intent = appSettings()
    ContextCompat.startActivity(this, intent, null)
}