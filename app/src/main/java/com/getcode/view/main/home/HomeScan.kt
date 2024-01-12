package com.getcode.view.main.home

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccountModal
import com.getcode.navigation.screens.BalanceModal
import com.getcode.navigation.screens.GetKinModal
import com.getcode.navigation.screens.GiveKinModal
import com.getcode.navigation.screens.HomeScreen
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.util.AnimationUtils
import com.getcode.util.addIf
import com.getcode.util.flagResId
import com.getcode.util.formatted
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.OnLifecycleEvent
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher
import com.getcode.view.main.connectivity.NetworkConnectionViewModel
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.home.components.BillManagementOptions
import com.getcode.view.main.home.components.HomeBill
import com.getcode.view.main.home.components.PaymentConfirmation
import com.getcode.view.main.home.components.PermissionsBlockingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    connectionViewModel: NetworkConnectionViewModel,
    deepLink: String? = null
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
                connectionViewModel = connectionViewModel,
                dataState = dataState,
                deepLink = deepLink
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun HomeScan(
    homeViewModel: HomeViewModel,
    dataState: HomeUiModel,
    connectionViewModel: NetworkConnectionViewModel,
    deepLink: String?,
) {
    val navigator = LocalCodeNavigator.current
    val scope = rememberCoroutineScope()


    var isPaused by rememberSaveable { mutableStateOf(false) }

    var kikCodeScannerView: KikCodeScannerView? by remember { mutableStateOf(null) }

    val context = LocalContext.current as Activity

    val focusManager = LocalFocusManager.current
    LaunchedEffect(dataState.isCameraScanEnabled) {
        if (dataState.isCameraScanEnabled) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(homeViewModel) {
        if (!deepLink.isNullOrBlank()) {
            homeViewModel.onCashLinkGrabStart()
        }
        if (!deepLink.isNullOrBlank() && !dataState.isDeepLinkHandled) {
            homeViewModel.openCashLink(deepLink)
        }
    }

    fun startScanPreview() {
        val view = kikCodeScannerView ?: return
        view.startPreview()
        homeViewModel.startScan(view)
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
        context = context,
        isPaused = isPaused,
        dataState = dataState,
        homeViewModel = homeViewModel,
        connectionViewModel = connectionViewModel,
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
        showBottomSheet = { showBottomSheet(it) }
    )

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                isPaused = false
                startScanPreview()
            }

            Lifecycle.Event.ON_STOP -> {
                stopScanPreview()
            }

            Lifecycle.Event.ON_PAUSE -> {
                isPaused = true
                homeViewModel.startSheetDismissTimer {
                    Timber.d("hiding from timeout")
                    navigator.hide()
                }
            }

            Lifecycle.Event.ON_RESUME -> {
                isPaused = false
                homeViewModel.stopSheetDismissTimer()
            }

            else -> Unit
        }
    }

    LaunchedEffect(navigator.isVisible) {
        if (!navigator.isVisible) {
            startScanPreview()
        } else {
            homeViewModel.stopScan()
        }
    }
    LaunchedEffect(dataState.isCameraScanEnabled) {
        if (dataState.isCameraScanEnabled) {
            startScanPreview()
        }
    }

    LaunchedEffect(dataState.billState.bill) {
        homeViewModel.resetScreenTimeout(context)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun BillContainer(
    modifier: Modifier = Modifier,
    context: Context,
    isPaused: Boolean,
    dataState: HomeUiModel,
    homeViewModel: HomeViewModel,
    connectionViewModel: NetworkConnectionViewModel,
    scannerView: @Composable () -> Unit,
    showBottomSheet: (HomeBottomSheet) -> Unit,
) {
    val onPermissionResult =
        { isGranted: Boolean ->
            homeViewModel.onCameraPermissionChanged(isGranted = isGranted)
        }

    val launcher = getPermissionLauncher(onPermissionResult)
    val connectionState by connectionViewModel.connectionStatus.collectAsState()

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
            .addIf(dataState.isCameraPermissionGranted != true) { Modifier.background(Color.Black) }
            .then(modifier)
    ) {
        if (dataState.isCameraPermissionGranted == true || dataState.isCameraPermissionGranted == null) {
            scannerView()

            var show by rememberSaveable {
                mutableStateOf(true)
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = show,
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
                LaunchedEffect(dataState.isCameraScanEnabled) {
                    if (dataState.isCameraScanEnabled) {
                        delay(500)
                        show = false
                    }
                }
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
                    val canDismiss = it == DismissValue.DismissedToEnd && updatedState.billState.canSwipeToDismiss
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
            DecorView(updatedState, connectionState, isPaused) { showBottomSheet(it) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            HomeBill(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                dismissState = billDismissState,
                dismissed = dismissed,
                bill = updatedState.billState.bill,
                transitionSpec = {
                    if (updatedState.presentationStyle is PresentationStyle.Slide) {
                        AnimationUtils.animationBillEnter
                    } else {
                        AnimationUtils.animationBillEnterSpring
                    } togetherWith if (updatedState.presentationStyle is PresentationStyle.Slide) {
                        AnimationUtils.animationBillExit
                    } else {
                        fadeOut()
                    }
                }
            )

            //Bill management options
            AnimatedVisibility(
                visible = billDismissState.targetValue == DismissValue.Default &&
                        updatedState.billState.bill != null &&
                        !updatedState.billState.hideBillButtons,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                BillManagementOptions(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    isSending = updatedState.isRemoteSendLoading,
                    onSend = { homeViewModel.onRemoteSend(context) },
                    onCancel = {
                        homeViewModel.cancelSend()
                    }
                )
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
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    contentAlignment = BottomCenter
                ) {
                    Column(
                        modifier = Modifier
                            .clip(
                                CodeTheme.shapes.medium.copy(
                                    bottomStart = ZeroCornerSize,
                                    bottomEnd = ZeroCornerSize
                                )
                            )
                            .background(Brand)
                            .padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.grid.x3
                            ),
                        horizontalAlignment = CenterHorizontally
                    ) {
                        Text(
                            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
                            style = CodeTheme.typography.subtitle1.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            text = stringResource(id = R.string.subtitle_youReceived)
                        )

                        Row {
                            val bill = updatedState.billState.bill as Bill.Cash
                            AmountArea(
                                amountText = bill.amount.formatted(),
                                currencyResId = bill.amount.rate.currency.flagResId,
                                isClickable = false
                            )

                        }
                        CodeButton(
                            onClick = { homeViewModel.cancelSend() },
                            buttonState = ButtonState.Filled,
                            text = stringResource(id = R.string.action_putInWallet)
                        )
                    }
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
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    contentAlignment = BottomCenter
                ) {
                    PaymentConfirmation(
                        confirmation = updatedState.billState.paymentConfirmation,
                        onSend = { homeViewModel.completePayment() },
                        onCancel = {
                            homeViewModel.rejectPayment()
                        }
                    )
                }
            }
        }
    }
}