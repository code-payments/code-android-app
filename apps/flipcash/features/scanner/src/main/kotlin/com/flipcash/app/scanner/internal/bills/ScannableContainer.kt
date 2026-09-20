package com.flipcash.app.scanner.internal.bills

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.TabBarAction
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.scanner.internal.GalleryScanButton
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.scanner.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission

/**
 * The scanner surface: the camera preview (via [scannerView]) and its permission states. Bills are
 * not drawn here — a presented bill renders at the app root
 * ([com.flipcash.app.bills.BillOverlay]) so it can appear over any screen. Scanner chrome is the
 * hoisted app nav bar, not an in-screen HUD.
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

        // Remembered because `observe` builds a fresh `stateIn(dataScope, Eagerly, ..)` on every
        // call and `dataScope` outlives the screen: calling it straight from a composable body
        // starts a DataStore collection per recomposition that nothing ever cancels.
        val featureFlags = LocalFeatureFlags.current
        val galleryFlag = remember(featureFlags) {
            featureFlags.observe(FeatureFlag.ScanFromGallery)
        }
        val galleryEnabled by galleryFlag.collectAsStateWithLifecycle()

        // Published to the tab bar rather than drawn here, so it sits on the bar's row beside the
        // pill and takes width from it instead of floating over the preview. Nothing needs to be
        // said about a presented bill: the bar hides itself whole while one is up, and this goes
        // with it.
        //
        // Outside the permission `when` on purpose: a picked photo needs no camera, and someone who
        // declined the camera is exactly who this is for.
        if (galleryEnabled) {
            TabBarAction(NavBarButton.Scanner) { modifier ->
                GalleryScanButton(onImagePicked = onImagePicked, modifier = modifier)
            }
        }
    }
}
