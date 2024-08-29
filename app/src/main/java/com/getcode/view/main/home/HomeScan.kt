package com.getcode.view.main.home

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.getcode.LocalBiometricsState
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.models.Bill
import com.getcode.models.DeepLinkRequest
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccountModal
import com.getcode.navigation.screens.BalanceModal
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.ChatListModal
import com.getcode.navigation.screens.ConnectAccount
import com.getcode.navigation.screens.EnterTipModal
import com.getcode.navigation.screens.GetKinModal
import com.getcode.navigation.screens.GiveKinModal
import com.getcode.navigation.screens.ShareDownloadLinkModal
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.PermissionCheck
import com.getcode.ui.components.getPermissionLauncher
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed
import com.getcode.ui.utils.measured
import com.getcode.view.login.notificationPermissionCheck
import com.getcode.view.main.home.components.BillManagementOptions
import com.getcode.view.main.home.components.CameraDisabledView
import com.getcode.view.main.camera.CodeScanner
import com.getcode.view.main.home.components.HomeBill
import com.getcode.view.main.home.components.LoginConfirmation
import com.getcode.view.main.home.components.PaymentConfirmation
import com.getcode.view.main.home.components.PermissionsBlockingView
import com.getcode.view.main.home.components.ReceivedKinConfirmation
import com.getcode.view.main.home.components.TipConfirmation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds


enum class HomeAction {
    NONE,
    ACCOUNT,
    GIVE_KIN,
    GET_KIN,
    BALANCE,
    SHARE_DOWNLOAD,
    TIP_CARD,
    CHAT
}

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    cashLink: String? = null,
    request: DeepLinkRequest? = null,
) {
    val navigator = LocalCodeNavigator.current
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
                cashLink = cashLink,
                request = request,
            )

            val notificationPermissionChecker = notificationPermissionCheck {  }
            val context = LocalContext.current
            LaunchedEffect(homeViewModel) {
                homeViewModel.eventFlow
                    .onEach {
                        when (it) {
                            HomeEvent.PresentTipEntry -> {
                                navigator.show(EnterTipModal())
                            }

                            is HomeEvent.SendIntent -> {
                                context.startActivity(it.intent)
                            }

                            HomeEvent.RequestNotificationPermissions -> {
                                notificationPermissionChecker(true)
                            }
                        }
                    }
                    .launchIn(this)
            }
        }
    }
}

@Composable
private fun HomeScan(
    homeViewModel: HomeViewModel,
    dataState: HomeUiModel,
    cashLink: String?,
    request: DeepLinkRequest?,
) {
    val navigator = LocalCodeNavigator.current
    val scope = rememberCoroutineScope()

    var isPaused by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var previewing by remember {
        mutableStateOf(false)
    }

    var cameraStarted by remember {
        mutableStateOf(dataState.autoStartCamera == true)
    }

    LaunchedEffect(previewing) {
        homeViewModel.onCameraScanning(previewing)
    }

    val focusManager = LocalFocusManager.current

    var cashLinkSaved by remember(cashLink) {
        mutableStateOf(cashLink)
    }

    var requestPayloadSaved by remember(request) {
        mutableStateOf(request)
    }

    val biometricsState = LocalBiometricsState.current
    LaunchedEffect(biometricsState, previewing, dataState.balance, cashLinkSaved, requestPayloadSaved) {
        if (previewing) {
            focusManager.clearFocus()
        }

        if (biometricsState.passed && !cashLinkSaved.isNullOrBlank()) {
            homeViewModel.openCashLink(cashLink)
            cashLinkSaved = null
        }

        if (biometricsState.passed && requestPayloadSaved != null && dataState.balance != null) {
            delay(500.milliseconds)
            homeViewModel.handleRequest(request)
            requestPayloadSaved = null
        }
    }

    fun handleAction(action: HomeAction) {
        scope.launch {
            when (action) {
                HomeAction.GIVE_KIN -> navigator.show(GiveKinModal)
                HomeAction.ACCOUNT -> navigator.show(AccountModal)
                HomeAction.GET_KIN -> navigator.show(GetKinModal)
                HomeAction.BALANCE -> navigator.show(BalanceModal)
                HomeAction.SHARE_DOWNLOAD -> navigator.show(ShareDownloadLinkModal)
                HomeAction.TIP_CARD -> {
                    if (dataState.tipCardConnected) {
                        homeViewModel.presentShareableTipCard()
                    } else {
                        navigator.show(ConnectAccount())
                    }
                }
                HomeAction.NONE -> Unit
                HomeAction.CHAT -> navigator.show(ChatListModal)
            }
        }
    }

    BillContainer(
        navigator = navigator,
        isPaused = isPaused,
        isCameraReady = previewing,
        isCameraStarted = cameraStarted,
        dataState = dataState,
        homeViewModel = homeViewModel,
        onStartCamera = { cameraStarted = true },
        scannerView = {
            CodeScanner(
                scanningEnabled = previewing,
                cameraGesturesEnabled = dataState.cameraGestures.enabled,
                onPreviewStateChanged = { previewing = it },
                onCodeScanned = {
                    if (previewing) {
                        homeViewModel.onCodeScan(it)
                    }
                }
            )
        },
        onAction = { handleAction(it) },
    )

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("onStart")
                isPaused = false
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.d("onStop")
                if (dataState.autoStartCamera == false) {
                    cameraStarted = false
                }
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
            previewing = false
        }
    }

    LaunchedEffect(navigator.isVisible) {
        previewing = !navigator.isVisible
    }

    LaunchedEffect(dataState.billState.bill) {
        if (dataState.billState.bill != null) {
            navigator.hide()
        }
        homeViewModel.resetScreenTimeout(context as Activity)
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
private fun BillContainer(
    modifier: Modifier = Modifier,
    navigator: CodeNavigator,
    isCameraReady: Boolean,
    isCameraStarted: Boolean,
    isPaused: Boolean,
    dataState: HomeUiModel,
    homeViewModel: HomeViewModel,
    scannerView: @Composable () -> Unit,
    onStartCamera: () -> Unit,
    onAction: (HomeAction) -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        when {
            LocalBiometricsState.current.isAwaitingAuthentication -> {
                // waiting for result
            }
            dataState.isCameraPermissionGranted == true || dataState.isCameraPermissionGranted == null -> {
                if (dataState.autoStartCamera == null) {
                    // waiting for result
                } else if (!dataState.autoStartCamera && !isCameraStarted) {
                    CameraDisabledView(modifier = Modifier.fillMaxSize()) {
                        onStartCamera()
                    }
                } else {
                    scannerView()
                }
            }
            else -> {
                PermissionsBlockingView(
                    modifier = Modifier.fillMaxSize(),
                    context = context,
                    onPermissionResult = onPermissionResult,
                    launcher = launcher
                )
            }
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
                delay(500)
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
            DecorView(updatedState, isCameraReady, isPaused) { onAction(it) }
        }

        var managementHeight by remember {
            mutableStateOf(0.dp)
        }

        val showManagementOptions by remember(updatedState.billState) {
            derivedStateOf {
                billDismissState.targetValue == DismissValue.Default &&
                        (updatedState.billState.valuation != null || updatedState.billState.bill is Bill.Tip) &&
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
            exit = fadeOut(tween(100)),
        ) {
            var canCancel by remember {
                mutableStateOf(false)
            }
            BillManagementOptions(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars),
                primaryAction = updatedState.billState.primaryAction,
                secondaryAction = updatedState.billState.secondaryAction,
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

            BackHandler(updatedState.billState.bill is Bill.Tip && canCancel) {
                homeViewModel.cancelSend()
            }
        }

        //Bill Received Bottom Dialog
        AnimatedVisibility(
            modifier = Modifier.align(BottomCenter),
            visible = (updatedState.billState.bill as? Bill.Cash)?.didReceive ?: false,
            enter = AnimationUtils.modalEnter,
            exit = AnimationUtils.modalExit,
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
            transitionSpec = AnimationUtils.modalAnimationSpec(),
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
                            if (updatedState.buyModule.enabled) {
                                if (updatedState.buyModule.available) {
                                    navigator.show(BuyMoreKinModal(showClose = true))
                                } else {
                                    TopBarManager.showMessage(
                                        TopBarManager.TopBarMessage(
                                            title = context.getString(R.string.error_title_buyModuleUnavailable),
                                            message = context.getString(R.string.error_description_buyModuleUnavailable),
                                            type = TopBarManager.TopBarMessageType.ERROR
                                        )
                                    )
                                }
                            } else {
                                navigator.show(BuySellScreen)
                            }
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
            transitionSpec = AnimationUtils.modalAnimationSpec(),
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

        // Tip Confirmation container
        AnimatedContent(
            modifier = Modifier.align(BottomCenter),
            targetState = updatedState.billState.tipConfirmation?.payload, // payload is constant across state changes
            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast),
            label = "tip confirmation",
        ) {
            if (it != null) {
                Box(
                    contentAlignment = BottomCenter
                ) {
                    TipConfirmation(
                        confirmation = updatedState.billState.tipConfirmation,
                        onSend = { homeViewModel.completeTipPayment() },
                        onCancel = { homeViewModel.cancelTip() }
                    )
                }
            }
        }
    }
}
