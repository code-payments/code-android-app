package com.getcode.view.main.home

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberDismissState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.theme.Black50
import com.getcode.theme.Brand
import com.getcode.theme.Gray50
import com.getcode.theme.White
import com.getcode.util.AnimationUtils
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.OnLifecycleEvent
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher
import com.getcode.view.main.account.AccountSheet
import com.getcode.view.main.balance.BalanceSheet
import com.getcode.view.main.getKin.GetKinSheet
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.giveKin.GiveKinSheet
import com.getcode.view.main.home.components.BillManagementOptions
import com.getcode.view.main.home.components.HomeBill
import com.getcode.view.main.home.components.HomeBottom
import com.getcode.view.main.home.components.PaymentConfirmation
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Timer
import kotlin.concurrent.timerTask


enum class HomeBottomSheet {
    NONE,
    ACCOUNT,
    GIVE_KIN,
    GET_KIN,
    BALANCE,
    FAQ
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModalSheetLayout(
    state: ModalBottomSheetState,
    sheetContent: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheetLayout(
        sheetState = state,
        sheetBackgroundColor = Brand,
        sheetContent = sheetContent,
        sheetShape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
    ) {
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScan(
    deepLink: String? = null
) {
    val homeViewModel = hiltViewModel<HomeViewModel>()
    val dataState by homeViewModel.uiFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val billVisibleState = remember { MutableTransitionState(false) }
    val receiveDialogVisibleState = remember { MutableTransitionState(false) }
    val paymentConfirmationVisibleState = remember { MutableTransitionState(false) }
    val balanceChangeToastVisibleState = remember { MutableTransitionState(false) }

    var isInitBottomSheet by remember { mutableStateOf(false) }
    var isReturnBackToBalance by remember { mutableStateOf(false) }
    val billDismissState =
        rememberDismissState(initialValue = DismissValue.Default, confirmStateChange = {
            it != DismissValue.DismissedToStart
        })
    var isPaused by remember { mutableStateOf(false) }

    var kikCodeScannerView: KikCodeScannerView? by remember { mutableStateOf(null) }

    val context = LocalContext.current as Activity
    val onPermissionResult =
        { isGranted: Boolean ->
            homeViewModel.onCameraPermissionChanged(isGranted = isGranted)
        }
    val launcher = getPermissionLauncher(onPermissionResult)
    val systemUiController = rememberSystemUiController()

    SideEffect {
        PermissionCheck.requestPermission(
            context = context,
            permission = Manifest.permission.CAMERA,
            shouldRequest = false,
            onPermissionResult = onPermissionResult,
            launcher = launcher
        )
    }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(dataState.isCameraScanEnabled) {
        if (dataState.isCameraScanEnabled) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(Unit) {
        systemUiController.setSystemBarsColor(Color.Black)
        if (!deepLink.isNullOrBlank()) {
            homeViewModel.onCashLinkGrabStart()
        }
        if (!deepLink.isNullOrBlank() && !dataState.isDeepLinkHandled) {
            homeViewModel.openCashLink(deepLink)

            Timer().schedule(timerTask {
                isInitBottomSheet = true
            }, 2500)
        } else {
            isInitBottomSheet = true
        }
    }

    val animationSpec = remember {
        Animatable(0f)
            .run {
                TweenSpec<Float>(durationMillis = 400, easing = LinearOutSlowInEasing)
            }
    }

    //////////////////////////////////////////////////////////////////////
    var isGiveKinSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }

    val giveKinSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec
    )

    //////////////////////////////////////////////////////////////////////
    var isBalanceSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }

    val balanceSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec
    )

    //////////////////////////////////////////////////////////////////////
    var isFaqSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var isFaqSheetCollapsing by rememberSaveable {
        mutableStateOf(false)
    }

    val faqSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec
    )

    //////////////////////////////////////////////////////////////////////
    var isAccountSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }

    val accountSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec
    )

    //////////////////////////////////////////////////////////////////////
    var isGetKinSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }

    val getKinSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec
    )

    LaunchedEffect(giveKinSheetState) {
        snapshotFlow {
            giveKinSheetState.isVisible
        }.distinctUntilChanged().collect { isVisible ->
            if (isVisible.not()) {
                isGiveKinSheetOpen = false
            }
        }
    }

    LaunchedEffect(getKinSheetState) {
        snapshotFlow {
            getKinSheetState.isVisible
        }.distinctUntilChanged().collect { isVisible ->
            if (isVisible.not()) {
                isGetKinSheetOpen = false
            }
        }
    }

    LaunchedEffect(accountSheetState) {
        snapshotFlow {
            accountSheetState.isVisible
        }.distinctUntilChanged().collect { isVisible ->
            if (isVisible.not()) {
                isAccountSheetOpen = false
            }
        }
    }

    LaunchedEffect(balanceSheetState) {
        snapshotFlow {
            balanceSheetState.isVisible
        }.distinctUntilChanged().collect { isVisible ->
            if (isVisible.not()) {
                isBalanceSheetOpen = false
            }
        }
    }

    fun hideSheet(bottomSheet: HomeBottomSheet) {
        scope.launch {
            when (bottomSheet) {
                HomeBottomSheet.GIVE_KIN -> {
                    giveKinSheetState.hide()
                    isGiveKinSheetOpen = false
                }
                HomeBottomSheet.NONE -> {}
                HomeBottomSheet.ACCOUNT -> {
                    accountSheetState.hide()
                }
                HomeBottomSheet.GET_KIN -> {
                    getKinSheetState.hide()
                }
                HomeBottomSheet.BALANCE -> {
                    balanceSheetState.hide()
                    isBalanceSheetOpen = false
                }
                HomeBottomSheet.FAQ -> {
                    isFaqSheetCollapsing = true
                    faqSheetState.hide()
                    isFaqSheetCollapsing = false
                }
            }
        }
    }

    fun hideBottomSheet() {
        HomeBottomSheet.values().forEach {
            hideSheet(it)
        }
        homeViewModel.onHideBottomSheet()
    }

    fun showBottomSheet(bottomSheet: HomeBottomSheet) {
        scope.launch {
            when (bottomSheet) {
                HomeBottomSheet.GIVE_KIN -> {
                    isGiveKinSheetOpen = true
                    giveKinSheetState.show()
                }
                HomeBottomSheet.NONE -> {}
                HomeBottomSheet.ACCOUNT -> {
                    isAccountSheetOpen = true
                    accountSheetState.show()
                }
                HomeBottomSheet.GET_KIN -> {
                    isGetKinSheetOpen = true
                    getKinSheetState.show()
                }
                HomeBottomSheet.BALANCE -> {
                    isBalanceSheetOpen = true
                    balanceSheetState.show()
                }
                HomeBottomSheet.FAQ -> {
                    isFaqSheetOpen = true
                    faqSheetState.show()
                }
            }
        }
        homeViewModel.onShowBottomSheet()
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

    BackHandler {
        if (dataState.isBottomSheetVisible) {
            hideBottomSheet()
        } else {
            context.finish()
        }
    }

    dataState.restrictionType?.let { restrictionType ->
        context.let { activity ->
            HomeRestricted(activity, restrictionType) {
                homeViewModel.logout(activity)
            }
        }
        return
    }

    val constraintLayoutBgColor =
        if (dataState.isCameraScanEnabled && dataState.isCameraPermissionGranted == true) Color.Black
        else Brand
    
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(constraintLayoutBgColor)
    ) {
        val (billContainer, billActions, permissionContainer, amountToast) = createRefs()

        if (dataState.isCameraPermissionGranted == true || dataState.isCameraPermissionGranted == null) {
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
        } else {
            Column(modifier = Modifier
                .constrainAs(permissionContainer) {
                    centerTo(parent)
                }
                .fillMaxWidth(0.85f)
            ) {
                Text(
                    modifier = Modifier.padding(bottom = 30.dp),
                    style = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
                    text = stringResource(R.string.subtitle_allowCameraAccess)
                )
                CodeButton(
                    onClick = {
                        PermissionCheck.requestPermission(
                            context = context,
                            permission = Manifest.permission.CAMERA,
                            shouldRequest = true,
                            onPermissionResult = onPermissionResult,
                            launcher = launcher
                        )
                    },
                    modifier = Modifier.align(CenterHorizontally),
                    text = stringResource(id = R.string.action_allowCameraAccess),
                    isMaxWidth = false,
                    isPaddedVertical = false,
                    shape = RoundedCornerShape(45.dp),
                    buttonState = ButtonState.Filled
                )
            }
        }

        // Composable animation for the side bar sheet
        AnimatedVisibility(
            visible = !dataState.isBillVisible || billDismissState.targetValue != DismissValue.Default,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DecorView { showBottomSheet(it) }
        }

        // Home bill composable?
        AnimatedVisibility(
            visibleState = billVisibleState,
            enter = if (dataState.isBillSlideInAnimated) {
                AnimationUtils.animationBillEnter
            } else {
                AnimationUtils.animationBillEnterSpring
            },
            exit = if (dataState.isBillSlideOutAnimated) {
                AnimationUtils.animationBillExit
            } else {
                fadeOut()
            },
        ) {
            HomeBill(
                modifier = Modifier
                    .constrainAs(billContainer) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                    },
                dismissState = billDismissState,
                billAmount = dataState.billAmount,
                payloadData = dataState.billPayloadData.orEmpty(),
                paymentRequest = dataState.paymentRequest
            )
        }

        //Bill management options
        AnimatedVisibility(
            visible = billDismissState.targetValue == DismissValue.Default && dataState.isBillVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .constrainAs(billActions) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(billContainer.bottom)
                    bottom.linkTo(parent.bottom)
                },
        ) {
            BillManagementOptions(
                showSend = !dataState.isReceiveDialogVisible && dataState.paymentRequest == null,
                showCancel = dataState.paymentRequest == null,
                isSending = dataState.isRemoteSendLoading,
                onSend = { homeViewModel.onRemoteSend(context) },
                onCancel = {
                    homeViewModel.hideBill(
                        isSent = false,
                        isVibrate = false
                    )
                }
            )
        }

        //Balance Changed Toast
        AnimatedVisibility(
            modifier = Modifier
                .constrainAs(amountToast) {
                    end.linkTo(parent.end, 25.dp)
                    bottom.linkTo(parent.bottom, 105.dp)
                },
            visibleState = balanceChangeToastVisibleState,
            enter = slideInVertically(animationSpec = tween(600), initialOffsetY = { it }) +
                    fadeIn(animationSpec = tween(500, 100)),
            exit = if (!isPaused)
                        slideOutVertically(animationSpec = tween(600), targetOffsetY = { it }) +
                        fadeOut(animationSpec = tween(500, 100))
                    else fadeOut(animationSpec = tween(0)),
        ) {
            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .clip(RoundedCornerShape(25.dp))
                    .background(Black50)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = dataState.balanceChangeToastText.orEmpty(),
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    //Bill Received Bottom Dialog
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visibleState = receiveDialogVisibleState,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 600, delayMillis = 450)
        ),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                    .background(Brand)
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                horizontalAlignment = CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(top = 15.dp),
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    text = stringResource(id = R.string.subtitle_youReceived)
                )

                Row {
                    val currencyCode = dataState.billAmount?.rate?.currency?.name
                    val currencyResId =
                        com.getcode.util.CurrencyUtils.getFlagByCurrency(currencyCode)

                    AmountArea(
                        amountText = dataState.billReceivedAmountText.orEmpty(),
                        currencyResId = currencyResId,
                        isClickable = false
                    )
                }
                CodeButton(
                    onClick = { homeViewModel.hideBill(isSent = false, isVibrate = false) },
                    buttonState = ButtonState.Filled,
                    text = stringResource(id = R.string.action_putInWallet)
                )
            }
        }
    }

    // Payment Confirmation container
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visibleState = paymentConfirmationVisibleState,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 600, delayMillis = 450)
        ),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        PaymentConfirmation(
            isSending = dataState.isRemoteSendLoading,
            onSend = {  },
            onCancel = {
                homeViewModel.hideBill(isSent = false, isVibrate = false)
            }
        )
    }

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
                homeViewModel.startSheetDismissTimer { hideBottomSheet() }
                balanceChangeToastVisibleState.targetState = false
            }
            Lifecycle.Event.ON_RESUME -> {
                isPaused = false
                homeViewModel.stopSheetDismissTimer()
            }
            else -> Unit
        }
    }

    LaunchedEffect(dataState.isBillVisible) {
        withContext(Dispatchers.Main) {
            if (billVisibleState.isIdle) {
                billVisibleState.targetState = dataState.isBillVisible
            }
        }
        if (dataState.isBillVisible) {
            hideBottomSheet()
        }
    }
    LaunchedEffect(dataState.isReceiveDialogVisible) {
        withContext(Dispatchers.Main) {
            receiveDialogVisibleState.targetState = dataState.isReceiveDialogVisible
        }
    }
    LaunchedEffect(dataState.paymentRequest) {
        withContext(Dispatchers.Main) {
            paymentConfirmationVisibleState.targetState = dataState.paymentRequest != null
        }
    }

    LaunchedEffect(dataState.isBalanceChangeToastVisible) {
        withContext(Dispatchers.Main) {
            balanceChangeToastVisibleState.targetState = dataState.isBalanceChangeToastVisible
        }
    }
    LaunchedEffect(dataState.isCameraScanEnabled) {
        if (dataState.isCameraScanEnabled) {
            startScanPreview()
        }
    }

    LaunchedEffect(billDismissState.currentValue) {
        if (billDismissState.currentValue != DismissValue.Default) {
            billVisibleState.targetState = false
        }
    }
    LaunchedEffect(billVisibleState.targetState) {
        if (!billVisibleState.targetState) {
            homeViewModel.hideBill()
        }
        homeViewModel.resetScreenTimeout(context)
    }
    LaunchedEffect(billVisibleState.currentState) {
        if (!billVisibleState.currentState) {
            billDismissState.reset()
        } else if (!dataState.isBillVisible) {
            billVisibleState.targetState = dataState.isBillVisible
        }
    }

    //wrap this with the visibility state so it does not re compose all the time
    //TODO enable composition for balance and give kin after launch
    ModalSheetLayout(
        giveKinSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideBottomSheet() }
        val onCloseQuickly = { hideBottomSheet() }
        if (isGiveKinSheetOpen) {
            GiveKinSheet(onClose, onCloseQuickly, homeViewModel = homeViewModel)
        }
    }

    //Balance sheet modal
    ModalSheetLayout(
        balanceSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideBottomSheet() }
        BalanceSheet(upPress = onClose) {
            isReturnBackToBalance = true
            showBottomSheet(HomeBottomSheet.FAQ)
        }
    }

    //Account sheet modal
    ModalSheetLayout(
        accountSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onCloseQuickly = { hideBottomSheet() }
        if (isAccountSheetOpen) {
            AccountSheet(homeViewModel = homeViewModel, onCloseQuickly)
        }
    }

    //FAQ sheet modal
    ModalSheetLayout(
        faqSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideSheet(HomeBottomSheet.FAQ) }
        if (isFaqSheetOpen) {
            HomeFaqSheet {
                if (isReturnBackToBalance) {
                    showBottomSheet(HomeBottomSheet.BALANCE)
                }
                onClose()
            }
        }
    }

    //FAQ sheet modal
    ModalSheetLayout(
        getKinSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideBottomSheet() }
        if (isGetKinSheetOpen) {
            GetKinSheet(homeViewModel, onClose)
        }
    }
}
