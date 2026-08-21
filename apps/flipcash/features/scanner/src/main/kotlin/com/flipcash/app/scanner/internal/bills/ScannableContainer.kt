package com.flipcash.app.scanner.internal.bills

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.scanner.internal.ScannerDecorItem
import com.flipcash.app.scanner.internal.ui.components.DecorView
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.scanner.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission

/**
 * The scanner surface: the camera preview (via [scannerView]) plus the HUD ([DecorView]). Bills are
 * no longer drawn here — a presented bill renders at the app root
 * ([com.flipcash.app.bills.BillOverlay]) so it can appear over any screen. This container only reads
 * [com.flipcash.app.session.SessionController.billState] to hide its HUD while a bill is up.
 */
@Composable
internal fun ScannableContainer(
    modifier: Modifier = Modifier,
    isPaused: Boolean,
    isPinching: Boolean = false,
    zoomRatio: Float = 1f,
    scannerView: @Composable () -> Unit,
    onAction: (ScannerDecorItem) -> Unit
) {
    val session = LocalSessionController.current!!
    val context = LocalContext.current
    val resources = LocalResources.current
    val onPermissionResult = { result: PermissionResult ->
        if (result == PermissionResult.PermanentlyDenied) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.action_allowCameraAccess),
                message = resources.getString(R.string.error_description_cameraAccessRequired),
                actions = listOf(
                    BottomBarAction.Ok,
                    BottomBarAction(
                        text = resources.getString(R.string.action_openSettings),
                        style = BottomBarManager.BottomBarButtonStyle.Filled50,
                        onClick = { context.launchAppSettings() }
                    )
                )
            )
        }
    }

    val cameraPermission = rememberCameraPermission { onPermissionResult(it) }

    LaunchedEffect(cameraPermission.status) {
        onPermissionResult(cameraPermission.status)
    }

    val state by session.state.collectAsStateWithLifecycle()
    val billState by session.billState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .testTag("scanner_view")
    ) {
        val availableUpdate by LocalAppUpdater.current.availableUpdate.collectAsStateWithLifecycle()

        when {
            LocalBiometricsState.current.isAwaitingAuthentication -> {
                // waiting for result
            }

            availableUpdate != null -> {
                // waiting for update
            }

            else -> {
                when (cameraPermission.status) {
                    PermissionResult.Denied -> {
                        CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                            cameraPermission.launch()
                        }
                    }
                    PermissionResult.NotRequested -> {
                        CameraPermissionsMissingView(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { cameraPermission.launch() }
                        )
                    }
                    PermissionResult.Granted -> scannerView()
                    PermissionResult.PermanentlyDenied -> {
                        CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                            context.launchAppSettings()
                        }
                    }
                }
            }
        }

        // Hide the HUD while a bill is presented — the bill now renders at the app root, above this.
        AnimatedVisibility(
            visible = billState.bill == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DecorView(
                state = state,
                billState = billState,
                isPaused = isPaused,
                isPinching = isPinching,
                zoomRatio = zoomRatio,
                onAction = onAction
            )
        }
    }
}
