package com.flipcash.app.scanner.internal.bills

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.flipcash.app.bills.AnimatedScannable
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.scanner.internal.ScannerDecorItem
import com.flipcash.app.scanner.internal.ui.components.DecorView
import com.flipcash.app.scanner.internal.ui.modals.ReceivedFundsConfirmation
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.updates.LocalAppUpdater
import com.getcode.ui.components.OnLifecycleEvent
import androidx.lifecycle.Lifecycle
import com.flipcash.features.scanner.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.core.measured
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.ui.utils.AnimationUtils
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberCameraPermission
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterialApi::class)
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

    val autoStart = state.autoStartCamera == true
    var cameraStarted by remember { mutableStateOf(autoStart) }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !autoStart) {
            cameraStarted = false
        }
    }

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

            else ->{
                when (cameraPermission.status) {
                    PermissionResult.Denied -> {
                        CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                            cameraPermission.launch()
                        }
                    }
                    PermissionResult.NotRequested -> {
                        CameraPermissionsMissingView(
                            modifier = Modifier.fillMaxSize(),
                            backgroundColor = Color.Black,
                            onClick = { cameraPermission.launch() }
                        )
                    }
                    PermissionResult.Granted -> {
                        if (!cameraStarted) {
                            CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                                cameraStarted = true
                            }
                        } else {
                            scannerView()
                        }
                    }
                    PermissionResult.PermanentlyDenied -> {
                        CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                            context.launchAppSettings()
                        }
                    }
                }
            }
        }

        val updatedState by rememberUpdatedState(state)
        val updatedBillState by rememberUpdatedState(billState)

        var dismissed by remember(updatedBillState.bill) {
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
                        session.dismissBill(PutInWallet)
                        dismissed = true
                    }
                    canDismiss
                }
            )
        }

        LaunchedEffect(dismissed) {
            if (dismissed) {
                delay(500.milliseconds)
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
                billState = updatedBillState,
                isPaused = isPaused,
                isPinching = isPinching,
                zoomRatio = zoomRatio,
                onAction = onAction
            )
        }

        var managementHeight by remember {
            mutableStateOf(0.dp)
        }

        val showManagementOptions by remember(updatedBillState) {
            derivedStateOf {
                billDismissState.targetValue == DismissValue.Default &&
                        updatedBillState.valuation != null
            }
        }

        AnimatedScannable(
            modifier = Modifier.fillMaxSize(),
            dismissState = billDismissState,
            dismissed = dismissed,
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                top = CodeTheme.dimens.grid.x2,
                bottom = managementHeight + CodeTheme.dimens.grid.x2
            ),
            bill = updatedBillState.bill,
            transitionSpec = {
                when (updatedState.billResult) {
                    BillDeterminationResult.None -> EnterTransition.None
                    Grabbed -> AnimationUtils.animationBillEnterGrabbed
                    PutInWallet -> AnimationUtils.animationBillEnterGive
                } togetherWith when (updatedState.billResult) {
                    BillDeterminationResult.None -> ExitTransition.None
                    Grabbed -> AnimationUtils.animationBillExitGrabbed
                    PutInWallet -> AnimationUtils.animationBillExitReturned
                }
            }
        )

        // Below-bill content, folded by scannable type. `displayedScannable` retains the
        // last shown scannable so an arm's modal can still animate OUT as `bill` returns to
        // null on dismiss (the arm stays mounted; only `visible` flips).
        var displayedScannable by remember { mutableStateOf<Scannable?>(null) }
        LaunchedEffect(updatedBillState.bill) {
            updatedBillState.bill?.let { displayedScannable = it }
        }

        when (val shown = displayedScannable) {
            is Scannable.Payable -> {
                //Bill management options
                AnimatedVisibility(
                    modifier = Modifier
                        .align(BottomCenter)
                        .measured { managementHeight = it.height },
                    visible = updatedBillState.bill is Scannable.Payable && showManagementOptions,
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
                            delay(500)
                            canCancel = true
                        }
                    }

                    BackHandler(canCancel) {
                        session.dismissBill(PutInWallet)
                    }
                }

                //Bill Received Bottom Dialog
                AnimatedVisibility(
                    modifier = Modifier.align(BottomCenter),
                    visible = (updatedBillState.bill as? Scannable.Payable)?.didReceive == true,
                    enter = AnimationUtils.modalEnter(billState.confirmationDelayMillis),
                    exit = AnimationUtils.modalExit,
                ) {
                    Box(
                        contentAlignment = BottomCenter
                    ) {
                        ReceivedFundsConfirmation(
                            bill = shown,
                            onClaim = { session.dismissBill(PutInWallet) }
                        )
                    }
                }
            }

            is Scannable.TipCard -> {
                // TODO(owner): the tip card's bottom modal (analogous to ReceivedFundsConfirmation).
                //   Gate `visible` on the live bill so it animates out on dismiss, and use `shown`
                //   (the retained Scannable.TipCard) for content so it persists through the exit:
                //   AnimatedVisibility(
                //       modifier = Modifier.align(BottomCenter),
                //       visible = updatedBillState.bill is Scannable.TipCard,
                //       enter = AnimationUtils.modalEnter(0),
                //       exit = AnimationUtils.modalExit,
                //   ) {
                //       TipCardModal(tipCard = shown, onDone = { session.dismissBill(PutInWallet) })
                //   }
            }

            null -> Unit
        }
    }
}
