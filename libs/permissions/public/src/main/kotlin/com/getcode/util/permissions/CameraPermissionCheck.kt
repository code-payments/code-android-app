package com.getcode.util.permissions

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.getcode.libs.permissions.R
import com.getcode.manager.TopBarManager

@Composable
fun cameraPermissionCheck(isShowError: Boolean = true, onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val permissionChecker = rememberPermissionHandler()
    val context = LocalContext.current
    var permissionRequested by remember { mutableStateOf(false) }
    val onPermissionError = {
        TopBarManager.showMessage(
            TopBarManager.TopBarMessage(
                title = context.getString(R.string.action_allowCameraAccess),
                message = context.getString(R.string.error_description_cameraAccessRequired),
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
    val launcher = getPermissionLauncher(Manifest.permission.CAMERA, onPermissionResult)
    val permissionCheck = { shouldRequest: Boolean ->
        permissionRequested = shouldRequest
        permissionChecker.request(
            permission = Manifest.permission.CAMERA,
            shouldRequest = shouldRequest,
            onPermissionResult = onPermissionResult,
            launcher = launcher
        )
    }

    return permissionCheck
}