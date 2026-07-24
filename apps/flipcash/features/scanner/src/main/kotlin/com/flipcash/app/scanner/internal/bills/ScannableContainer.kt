package com.flipcash.app.scanner.internal.bills

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.app.ActivityManager
import android.os.Build
import androidx.compose.animation.togetherWith
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.flipcash.app.bills.AnimatedScannable
import com.flipcash.app.bills.components.cards.LocalTipCardBackdrop
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.bills.components.cards.TipCardOpaqueFallback
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.scanner.internal.ScannerDecorItem
import com.flipcash.app.scanner.internal.ui.components.DecorView
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.updates.LocalAppUpdater
import com.getcode.ui.components.OnLifecycleEvent
import androidx.lifecycle.Lifecycle
import com.flipcash.app.scanner.internal.bills.decor.ScannableDecoratorContext
import com.flipcash.app.scanner.internal.bills.decor.ScannableDecorator
import com.flipcash.features.scanner.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.scanner.views.CameraDisabledView
import com.getcode.ui.scanner.views.CameraPermissionsMissingView
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed
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
    previewView: PreviewView? = null,
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

    // Tip affordability (min-tip balance check) is owned by the tipping coordinator and
    // surfaced through the shared selection state, so the scanner reads it without
    // depending on the tipping module.
    val tipSelection by LocalTipCoordinator.current.selection.collectAsStateWithLifecycle()

    val autoStart = state.autoStartCamera == true
    var cameraStarted by remember { mutableStateOf(autoStart) }

    // Window-space origin of the scanner surface, used to align the blurred camera backdrop under
    // the tip card wherever it sits.
    var containerOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    // The frosted camera backdrop snapshots the feed (a GPU→CPU readback). Gate it to devices that
    // can absorb that — API 31+ for the blur, and not low-RAM. Everywhere else the card falls back
    // to an opaque approximation of the frosted tone.
    val supportsFrostedTipCard = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !autoStart) {
            cameraStarted = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .onGloballyPositioned { containerOriginInWindow = it.positionInWindow() }
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

        // Not keyed on the bill: it must stay true while the swiped-off card is being removed so
        // the outgoing content stays hidden through its exit instead of snapping back to center.
        // Reset explicitly when a fresh bill appears.
        var dismissed by remember { mutableStateOf(false) }
        LaunchedEffect(updatedBillState.bill) {
            if (updatedBillState.bill != null) dismissed = false
        }

        // bill dismiss state, restarted for every bill
        val billDismissState = remember(updatedBillState.bill) {
            DismissState(
                initialValue = DismissValue.Default,
                // Only gate whether the swipe is allowed. Removing the bill here (mid-swipe) would
                // recreate this DismissState and snap the card's offset back to center before the
                // exit slide — the "reset then dismiss" stutter. Removal happens after the swipe
                // settles the card off-screen (see below).
                confirmStateChange = {
                    it == DismissValue.DismissedToEnd && updatedBillState.canSwipeToDismiss
                }
            )
        }

        // Once the swipe has carried the card off-screen (currentValue leaves Default), hide it and
        // remove the bill. The card is already out of view, so the AnimatedContent exit re-shows
        // nothing and there's no offset reset.
        LaunchedEffect(billDismissState) {
            snapshotFlow { billDismissState.currentValue }
                .collect { value ->
                    if (value != DismissValue.Default) {
                        dismissed = true
                        session.dismissBill(PutInWallet)
                    }
                }
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
                // The tip card always shows, but its modal only slides up when the
                // viewer can afford the minimum tip; otherwise the tip decorator prompts
                // to add money.
                billDismissState.targetValue == DismissValue.Default &&
                        (updatedBillState.valuation != null ||
                                (updatedBillState.bill is Scannable.TipCard && tipSelection.canTip))
            }
        }

        // When the tip modal is up, pin the tip card just above it: reserve the modal's height as
        // bottom inset AND bottom-align the card (bias 0 = centered, 1 = bottom-aligned). Otherwise
        // the card centers in the region above the (tall) modal and floats high. Both are animated
        // so the card slides from centered down to just above the modal as it enters, and back.
        val tipModalUp = managementHeight > 0.dp && updatedBillState.bill is Scannable.TipCard
        // Drive the card's move with the SAME timing as the tip modal's enter (see
        // AnimationUtils.modalEnter → ModalAnimationSpeed.Normal): the card holds centered during the
        // modal's start delay, then slides up in lockstep with the modal instead of lagging behind.
        val modalSpeed = ModalAnimationSpeed.Normal(updatedBillState.confirmationDelayMillis)
        val offset = if (updatedBillState.bill is Scannable.TipCard) CodeTheme.dimens.grid.x8 else CodeTheme.dimens.grid.x2
        val billBottomInset by animateDpAsState(
            targetValue = managementHeight + offset,
            animationSpec = tween(durationMillis = modalSpeed.duration, delayMillis = modalSpeed.delay),
            label = "billBottomInset",
        )
        val billVerticalBias by animateFloatAsState(
            targetValue = if (tipModalUp) 1f else 0f,
            animationSpec = tween(durationMillis = modalSpeed.duration, delayMillis = modalSpeed.delay),
            label = "billVerticalBias",
        )

        // Frosted-camera backdrop behind the tip card. Snapshots the feed once (see
        // rememberCameraTipCardBackdrop) only while a tip card is up and only on capable devices.
        val isTipCard = updatedBillState.bill is Scannable.TipCard
        val tipCardBackdrop = rememberCameraTipCardBackdrop(
            previewView = previewView,
            enabled = isTipCard && supportsFrostedTipCard,
            containerOriginInWindow = containerOriginInWindow,
        )

        // Where the frosted backdrop is unavailable, render the tip card as an opaque approximation
        // of the frosted tone instead of a translucent panel over the (stutter-prone) live camera.
        val useOpaqueFallback = isTipCard && !supportsFrostedTipCard

        CompositionLocalProvider(
            LocalTipCardBackdrop provides tipCardBackdrop,
            LocalTipCardColor provides if (useOpaqueFallback) TipCardOpaqueFallback else Color.Unspecified,
            LocalTipCardBaseAlpha provides if (useOpaqueFallback) 1f else LocalTipCardBaseAlpha.current,
        ) {
            AnimatedScannable(
                modifier = Modifier.fillMaxSize(),
                dismissState = billDismissState,
                dismissed = dismissed,
                contentPadding = PaddingValues(
                    start = CodeTheme.dimens.inset,
                    end = CodeTheme.dimens.inset,
                    top = CodeTheme.dimens.grid.x2,
                    bottom = billBottomInset,
                ),
                scannableAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = billVerticalBias),
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
        }

        // Below-bill content, owned by the scannable type (see `overlays/ScannableOverlays`).
        // `displayedScannable` retains the last shown scannable so an overlay can still animate
        // OUT as `bill` returns to null on dismiss (the overlay stays mounted; only `visible` flips).
        var displayedScannable by remember { mutableStateOf<Scannable?>(null) }
        LaunchedEffect(updatedBillState.bill) {
            updatedBillState.bill?.let { displayedScannable = it }
        }

        displayedScannable?.let { ScannableDecorator.forScannable(it) }?.let { overlays ->
            val overlayContext = ScannableDecoratorContext(
                liveBill = updatedBillState.bill,
                billState = updatedBillState,
                isRemoteSendLoading = updatedState.isRemoteSendLoading,
                showManagementOptions = showManagementOptions,
                onManagementHeightMeasured = { managementHeight = it },
                onDismiss = { session.dismissBill(PutInWallet) },
            )
            with(overlays) { Content(overlayContext) }
        }
    }
}
