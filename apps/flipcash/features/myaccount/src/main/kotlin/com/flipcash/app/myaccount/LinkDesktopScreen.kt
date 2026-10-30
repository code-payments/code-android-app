package com.flipcash.app.myaccount

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.devicelink.ui.PhoneLinkScreen
import com.flipcash.app.myaccount.internal.linkdesktop.LinkDesktopViewModel
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.scanner.CodeScanner
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission

/**
 * Android entry for linking a desktop. The flow and screens are common (device-link); this file
 * only supplies what is Android-specific: the Hilt view model, the camera permission and the ML Kit
 * scanner.
 */
@Composable
fun LinkDesktopScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<LinkDesktopViewModel>()
    PhoneLinkScreen(
        controller = viewModel.controller,
        scanner = { onScanned -> QrScanner(onScanned) },
        onDone = { navigator.pop() },
    )
}

@Composable
private fun QrScanner(onScanned: (String) -> Unit) {
    val camera = rememberCameraPermission()
    if (camera.status != PermissionResult.Granted) {
        CameraDisabledView(modifier = Modifier.fillMaxSize()) { camera.launch() }
        return
    }
    CodeScanner(
        scanningEnabled = true,
        cameraGesturesEnabled = false,
        modifier = Modifier.fillMaxSize(),
        onPinchStateChanged = { _, _ -> },
        onPreviewStateChanged = { },
        onCodeScanned = { result ->
            if (result is CodeScanResult.QrCode) result.results.forEach(onScanned)
        },
    )
}
