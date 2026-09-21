package com.flipcash.app.scanner.internal.bills

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.scanner.internal.GalleryScanButton
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.scanner.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The scanner surface: the camera preview (via [scannerView]) and its permission states. Bills are
 * not drawn here — a presented bill renders at the app root
 * ([com.flipcash.app.bills.BillOverlay]) so it can appear over any screen. The only in-screen
 * chrome is the gallery button in the top-right corner; the tab bar is hoisted to the app root.
 */
@Composable
internal fun ScannableContainer(
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    scannerView: @Composable () -> Unit,
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

    // The app-root haze source is no use here: it wraps `AppNavHost`, and this screen is *inside*
    // it, so a blur hung off it would be sampling a layer that contains itself. The scanner keeps
    // its own source, scoped to the preview only, with the button deliberately outside it.
    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .testTag("scanner_view")
    ) {
        val availableUpdate by LocalAppUpdater.current.availableUpdate.collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
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
        }

        // Outside the permission `when` on purpose: a picked photo needs no camera, and someone
        // who declined the camera is exactly who this is for.
        //
        // Hidden while a bill is up. `NavBillOverlayEntryDecorator` draws the bill after this
        // entry's content, so the bill already covers the button -- but leaving it composed under
        // the scrim means a live control is sitting behind something modal, which is worth not
        // having rather than relying on the overlay to swallow every touch.
        if (billState.bill == null) {
            GalleryScanButton(
                onImagePicked = onImagePicked,
                hazeState = hazeState,
                // Top-right, clear of the status bar. This corner rather than the bottom because
                // the bottom belongs to the hoisted tab bar, and a control down there has to be
                // positioned around a bar this screen does not own.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(CodeTheme.dimens.inset)
            )
        }
    }
}
