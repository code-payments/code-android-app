package com.getcode.view.login

import android.Manifest
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.util.IntentUtils
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher

@Composable
fun cameraPermissionCheck(isShowError: Boolean = true, onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    val onPermissionError = {
        TopBarManager.showMessage(
            TopBarManager.TopBarMessage(
                title = context.getString(R.string.action_allowCameraAccess),
                message = context.getString(R.string.error_description_cameraAccessRequired),
                type = TopBarManager.TopBarMessageType.ERROR,
                secondaryText = context.getString(R.string.action_openSettings),
                secondaryAction = { IntentUtils.launchAppSettings() }
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
        permissionRequested = shouldRequest
        PermissionCheck.requestPermission(
            context = context,
            permission = Manifest.permission.CAMERA,
            shouldRequest = shouldRequest,
            onPermissionResult = onPermissionResult,
            launcher = launcher
        )
    }

    return permissionCheck
}