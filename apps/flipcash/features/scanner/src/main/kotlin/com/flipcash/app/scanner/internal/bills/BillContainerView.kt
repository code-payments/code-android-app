package com.flipcash.app.scanner.internal.bills

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.core.LocalSessionController
import com.flipcash.app.core.PresentationStyle
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.scanner.internal.DecorView
import com.flipcash.app.scanner.internal.ScannerDecorItem
import com.flipcash.features.scanner.R
import com.getcode.manager.TopBarManager
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.core.measured
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.ui.utils.AnimationUtils
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.getPermissionLauncher
import com.getcode.util.permissions.rememberPermissionHandler
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun BillContainer(
    modifier: Modifier = Modifier,
    isCameraReady: Boolean,
    isCameraStarted: Boolean,
    isPaused: Boolean,
    scannerView: @Composable () -> Unit,
    onStartCamera: () -> Unit,
    onAction: (ScannerDecorItem) -> Unit
) {
    val session = LocalSessionController.currentOrThrow

    val context = LocalContext.current as Activity
    val onPermissionResult = { result: PermissionResult ->
        session.onCameraPermissionResult(result)
        if (result == PermissionResult.ShouldShowRationale) {
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
    }

    val cameraPermissionLauncher =
        getPermissionLauncher(Manifest.permission.CAMERA, onPermissionResult)

    val permissionChecker = rememberPermissionHandler()

    val checkPermission = { shouldRequest: Boolean ->
        permissionChecker.request(
            permission = Manifest.permission.CAMERA,
            shouldRequest = shouldRequest,
            onPermissionResult = onPermissionResult,
            launcher = cameraPermissionLauncher
        )
    }

    SideEffect {
        checkPermission(false)
    }

    val state by session.state.collectAsState()
    val billState by session.billState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        when {
            LocalBiometricsState.current.isAwaitingAuthentication -> {
                // waiting for result
            }

            state.isCameraPermissionGranted == true || state.isCameraPermissionGranted == null -> {
                if (state.autoStartCamera == null) {
                    // waiting for result
                } else if (!state.autoStartCamera!! && !isCameraStarted) {
                    CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                        onStartCamera()
                    }
                } else {
                    scannerView()
                }
            }

            else -> {
                CameraPermissionsMissingView(
                    modifier = Modifier.fillMaxSize(),
                    onClick = { checkPermission(true) }
                )
            }
        }

        val updatedState by rememberUpdatedState(state)
        val updatedBillState by rememberUpdatedState(billState)

        var dismissed by remember {
            mutableStateOf(false)
        }

        // bill dismiss state, restarted for every bill
        val billDismissState = remember(updatedBillState.bill) {
            DismissState(
                initialValue = DismissValue.Default,
                confirmStateChange = {
                    val canDismiss =
                        it == DismissValue.DismissedToEnd && updatedBillState.canSwipeToDismiss
                    if (canDismiss) {
                        session.cancelSend()
                        dismissed = true
                    }
                    canDismiss
                }
            )
        }

        LaunchedEffect(dismissed) {
            if (dismissed) {
                delay(500)
                dismissed = false
            }
        }

        // Composable animation for the decor
        AnimatedVisibility(
            visible = updatedBillState.bill == null || billDismissState.targetValue != DismissValue.Default,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DecorView(
                state = updatedState,
                isPaused = isPaused,
                onAction = onAction
            )
        }

        var managementHeight by remember {
            mutableStateOf(0.dp)
        }

        val showManagementOptions by remember(updatedBillState) {
            derivedStateOf {
                billDismissState.targetValue == DismissValue.Default &&
                        updatedBillState.valuation != null &&
                        !updatedBillState.hideBillButtons
            }
        }

        AnimatedBill(
            modifier = Modifier.fillMaxSize(),
            dismissState = billDismissState,
            dismissed = dismissed,
            contentPadding = PaddingValues(bottom = managementHeight),
            bill = updatedBillState.bill,
            transitionSpec = {
                if (updatedState.presentationStyle is PresentationStyle.Slide) {
                    AnimationUtils.animationBillEnterGive
                } else {
                    AnimationUtils.animationBillEnterGrabbed
                } togetherWith if (updatedState.presentationStyle is PresentationStyle.Slide) {
                    AnimationUtils.animationBillExitReturned
                } else {
                    AnimationUtils.animationBillExitGrabbed
                }
            }
        )

        //Bill management options
        AnimatedVisibility(
            modifier = Modifier
                .align(BottomCenter)
                .measured { managementHeight = it.height },
            visible = showManagementOptions,
            enter = fadeIn(),
            exit = fadeOut(tween(100)),
        ) {
            var canCancel by remember {
                mutableStateOf(false)
            }
            BillManagementOptions(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars),
                primaryAction = updatedBillState.primaryAction,
                secondaryAction = updatedBillState.secondaryAction,
                isSending = updatedState.isRemoteSendLoading,
                isInteractable = canCancel,
            )

            LaunchedEffect(transition.isRunning, transition.targetState) {
                // wait for spring settle to enable cancel to not prematurely cancel
                // the enter. doing so causing the exit of the bill to not run, or run its own dismiss animation
                if (transition.targetState == EnterExitState.Visible && transition.currentState == transition.targetState) {
                    delay(300)
                    canCancel = true
                }
            }

            BackHandler(canCancel) {
                session.cancelSend()
            }
        }

        //Bill Received Bottom Dialog
        AnimatedVisibility(
            modifier = Modifier.align(BottomCenter),
            visible = (updatedBillState.bill as? Bill.Cash)?.didReceive ?: false,
            enter = AnimationUtils.modalEnter,
            exit = AnimationUtils.modalExit,
        ) {
            if (updatedBillState.bill != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
//                    ReceivedKinConfirmation(
//                        bill = updatedState.billState.bill as Bill.Cash,
//                        onClaim = { session.cancelSend() }
//                    )
                }
            }
        }
    }
}