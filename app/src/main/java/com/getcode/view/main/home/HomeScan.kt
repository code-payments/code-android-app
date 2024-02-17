package com.getcode.view.main.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.getcode.models.Bill
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccountModal
import com.getcode.navigation.screens.BalanceModal
import com.getcode.navigation.screens.GetKinModal
import com.getcode.navigation.screens.GiveKinModal
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.PermissionCheck
import com.getcode.ui.components.getPermissionLauncher
import com.getcode.ui.components.startupLog
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.measured
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.main.home.components.BillManagementOptions
import com.getcode.view.main.home.components.HomeBill
import com.getcode.view.main.home.components.LoginConfirmation
import com.getcode.view.main.home.components.PaymentConfirmation
import com.getcode.view.main.home.components.PermissionsBlockingView
import com.getcode.view.main.home.components.ReceivedKinConfirmation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber


enum class HomeBottomSheet {
    NONE,
    ACCOUNT,
    GIVE_KIN,
    GET_KIN,
    BALANCE
}

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    deepLink: String? = null,
    requestPayload: String? = null,
) {
    val dataState by homeViewModel.uiFlow.collectAsState()

    when (val restrictionType = dataState.restrictionType) {
        RestrictionType.ACCESS_EXPIRED,
        RestrictionType.FORCE_UPGRADE,
        RestrictionType.TIMELOCK_UNLOCKED -> {
            HomeRestricted(restrictionType) {
                homeViewModel.logout(it)
            }
        }

        null -> {
            HomeScan(
                homeViewModel = homeViewModel,
                dataState = dataState,
                deepLink = deepLink,
                requestPayload = requestPayload,
            )

            val context = LocalContext.current
            LaunchedEffect(homeViewModel) {
                homeViewModel.eventFlow
                    .filterIsInstance<HomeEvent.OpenUrl>()
                    .map { it.url }
                    .onEach {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                it.toUri()
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }.launchIn(this)
            }
        }
    }
}

@Composable
private fun HomeScan(
    homeViewModel: HomeViewModel,
    dataState: HomeUiModel,
    deepLink: String?,
    requestPayload: String?,
) {
    val navigator = LocalCodeNavigator.current
    val scope = rememberCoroutineScope()

    var isPaused by remember { mutableStateOf(false) }

    var kikCodeScannerView: KikCodeScannerView? by remember { mutableStateOf(null) }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(dataState.isCameraScanEnabled) {
        if (dataState.isCameraScanEnabled) {
            focusManager.clearFocus()
        }
    }

    var deepLinkSaved by remember(deepLink) {
        mutableStateOf(deepLink)
    }

    var requestPayloadSaved by remember(requestPayload) {
        mutableStateOf(requestPayload)
    }

    LaunchedEffect(kikCodeScannerView?.previewing, dataState.balance, deepLinkSaved, requestPayloadSaved) {
        if (kikCodeScannerView?.previewing == true) {
            if (!deepLinkSaved.isNullOrBlank()) {
                delay(500)
                homeViewModel.openCashLink(deepLink)
                deepLinkSaved = null
            }

            if (!requestPayloadSaved.isNullOrBlank() && dataState.balance != null) {
                delay(500)
                homeViewModel.handleRequest(requestPayload)
                requestPayloadSaved = null
            }
        }
    }

    LaunchedEffect(kikCodeScannerView?.previewing) {
        val view = kikCodeScannerView ?: return@LaunchedEffect
        if (view.previewing) { // kick off preview scanning once preview established
            homeViewModel.startScan(view)
        }
    }

    fun startScanPreview() {
        val view = kikCodeScannerView ?: return
        // establish preview
        view.startPreview()
    }

    fun stopScanPreview() {
        kikCodeScannerView?.stopPreview()
        Timber.i("Stop")
        homeViewModel.stopScan()
    }

    fun showBottomSheet(bottomSheet: HomeBottomSheet) {
        scope.launch {
            when (bottomSheet) {
                HomeBottomSheet.GIVE_KIN -> navigator.show(GiveKinModal)
                HomeBottomSheet.ACCOUNT -> navigator.show(AccountModal)
                HomeBottomSheet.GET_KIN -> navigator.show(GetKinModal)
                HomeBottomSheet.BALANCE -> navigator.show(BalanceModal)
                HomeBottomSheet.NONE -> Unit
            }
        }
    }

    BillContainer(
        navigator = navigator,
        isPaused = isPaused,
        dataState = dataState,
        homeViewModel = homeViewModel,
        scannerView = {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0, 0, 5, 5)),
                factory = { context ->
                    KikCodeScannerView(context).apply {
                        kikCodeScannerView = this
                        startScanPreview()
                    }
                },
                update = { }
            )
        },
        isCameraReady = kikCodeScannerView?.previewing == true,
        showBottomSheet = { showBottomSheet(it) }
    )

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("onStart")
                isPaused = false
                startScanPreview()
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.d("onStop")
                stopScanPreview()
            }

            Lifecycle.Event.ON_PAUSE -> {
                Timber.d("onPause")
                isPaused = true
                homeViewModel.startSheetDismissTimer {
                    Timber.d("hiding from timeout")
                    navigator.hide()
                }
            }

            Lifecycle.Event.ON_RESUME -> {
                Timber.d("onResume")
                isPaused = false
                homeViewModel.stopSheetDismissTimer()
            }

            else -> Unit
        }
    }

    DisposableEffect(LocalCodeNavigator.current) {
        onDispose {
            kikCodeScannerView?.stopPreview()
        }
    }

    LaunchedEffect(navigator.isVisible) {
        if (!navigator.isVisible) {
            kikCodeScannerView?.let(homeViewModel::startScan)
        } else {
            homeViewModel.stopScan()
        }
    }

    val context = LocalContext.current as Activity
    LaunchedEffect(dataState.billState.bill) {
        homeViewModel.resetScreenTimeout(context)
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
private fun BillContainer(
    modifier: Modifier = Modifier,
    navigator: CodeNavigator,
    isPaused: Boolean,
    isCameraReady: Boolean,
    dataState: HomeUiModel,
    homeViewModel: HomeViewModel,
    scannerView: @Composable () -> Unit,
    showBottomSheet: (HomeBottomSheet) -> Unit,
) {
    val onPermissionResult =
        { isGranted: Boolean ->
            homeViewModel.onCameraPermissionChanged(isGranted = isGranted)
        }

    val launcher = getPermissionLauncher(onPermissionResult)
    val context = LocalContext.current as Activity

    SideEffect {
        PermissionCheck.requestPermission(
            context = context,
            permission = Manifest.permission.CAMERA,
            shouldRequest = false,
            onPermissionResult = onPermissionResult,
            launcher = launcher
        )
    }


    LaunchedEffect(isCameraReady) {
        if (isCameraReady) {
            startupLog("camera ready")
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .addIf(dataState.isCameraPermissionGranted != true) { Modifier.background(Color.Black) }
            .then(modifier)
    ) {
        if (dataState.isCameraPermissionGranted == true || dataState.isCameraPermissionGranted == null) {
            scannerView()

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                // camera isn't really usable on an emulator so don't fade in wacky
                // camera feed
                visible = !isCameraReady,
                enter = fadeIn(
                    animationSpec = tween(AnimationUtils.animationTime)
                ),
                exit = fadeOut(tween(AnimationUtils.animationTime))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(CodeTheme.colors.background)
                )
            }
        } else {
            PermissionsBlockingView(
                modifier = Modifier
                    .align(Center)
                    .fillMaxWidth(0.85f),
                context = context,
                onPermissionResult = onPermissionResult,
                launcher = launcher
            )
        }

        val updatedState by rememberUpdatedState(newValue = dataState)

        var dismissed by remember {
            mutableStateOf(false)
        }

        // bill dismiss state, restarted for every bill
        val billDismissState = remember(updatedState.billState.bill) {
            DismissState(
                initialValue = DismissValue.Default,
                confirmStateChange = {
                    val canDismiss =
                        it == DismissValue.DismissedToEnd && updatedState.billState.canSwipeToDismiss
                    if (canDismiss) {
                        homeViewModel.cancelSend()
                        dismissed = true
                    }
                    canDismiss
                }
            )
        }

        LaunchedEffect(dismissed) {
            if (dismissed) {
                delay(300)
                dismissed = false
            }
        }

        // Composable animation for the side bar sheet
        AnimatedVisibility(
            visible = updatedState.billState.bill == null || billDismissState.targetValue != DismissValue.Default,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DecorView(updatedState, isPaused) { showBottomSheet(it) }
        }

        var managementHeight by remember {
            mutableStateOf(0.dp)
        }

        val showManagementOptions by remember(updatedState.billState) {
            derivedStateOf {
                billDismissState.targetValue == DismissValue.Default &&
                        updatedState.billState.valuation != null &&
                        !updatedState.billState.hideBillButtons
            }
        }

        HomeBill(
            modifier = Modifier.fillMaxSize(),
            dismissState = billDismissState,
            dismissed = dismissed,
            contentPadding = PaddingValues(bottom = managementHeight),
            bill = updatedState.billState.bill,
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
            exit = fadeOut(),
        ) {
            var canCancel by remember {
                mutableStateOf(false)
            }
            BillManagementOptions(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars),
                showSend = !updatedState.giveRequestsEnabled,
                isSending = updatedState.isRemoteSendLoading,
                onSend = { homeViewModel.onRemoteSend(context) },
                canCancel = canCancel,
                onCancel = {
                    homeViewModel.cancelSend()
                }
            )

            LaunchedEffect(transition.isRunning, transition.targetState) {
                // wait for spring settle to enable cancel to not prematurely cancel
                // the enter. doing so causing the exit of the bill to not run, or run its own dismiss animation
                if (transition.targetState == EnterExitState.Visible && transition.currentState == transition.targetState) {
                    delay(300)
                    canCancel = true
                }
            }
        }

        //Bill Received Bottom Dialog
        AnimatedVisibility(
            modifier = Modifier.align(BottomCenter),
            visible = (updatedState.billState.bill as? Bill.Cash)?.didReceive ?: false,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 600, delayMillis = 450)
            ),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            if (updatedState.billState.bill != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    ReceivedKinConfirmation(
                        bill = updatedState.billState.bill as Bill.Cash,
                        onClaim = { homeViewModel.cancelSend() }
                    )
                }
            }
        }

        // Payment Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = updatedState.billState.paymentConfirmation?.payload, // payload is constant across state changes
            transitionSpec = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 600, delayMillis = 450)
                ) togetherWith slideOutVertically(targetOffsetY = { it })
            },
            label = "payment confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    PaymentConfirmation(
                        confirmation = updatedState.billState.paymentConfirmation,
                        balance = updatedState.balance,
                        onAddKin = {
                            homeViewModel.rejectPayment()
                            navigator.show(GetKinModal)
                        },
                        onSend = { homeViewModel.completePayment() },
                        onCancel = {
                            homeViewModel.rejectPayment()
                        }
                    )
                }
            }
        }

        // Login Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = updatedState.billState.loginConfirmation?.payload, // payload is constant across state changes
            transitionSpec = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 600, delayMillis = 450)
                ) togetherWith slideOutVertically(targetOffsetY = { it })
            },
            label = "login confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    LoginConfirmation(
                        confirmation = updatedState.billState.loginConfirmation,
                        onSend = { homeViewModel.completeLogin() },
                        onCancel = {
                            homeViewModel.rejectLogin()
                        }
                    )
                }
            }
        }
    }
}