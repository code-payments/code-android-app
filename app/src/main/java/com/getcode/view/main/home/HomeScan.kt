package com.getcode.view.main.home

import android.Manifest
import android.app.Activity
import android.os.Debug
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.theme.*
import com.getcode.util.AnimationUtils
import com.getcode.view.camera.KikCodeScannerView
import com.getcode.view.components.*
import com.getcode.view.main.account.AccountSheet
import com.getcode.view.main.balance.BalanceSheet
import com.getcode.view.main.getKin.GetKinSheet
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.giveKin.GiveKinSheet
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
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

    //delay complex compose operations for faster launch time
    LaunchedEffect(Unit) {
        delay(400)
        if (!balanceSheetState.isVisible) {
            isBalanceSheetOpen = true
        }
        delay(100)
        if (!giveKinSheetState.isVisible) {
            isGiveKinSheetOpen = true
        }
    }

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
        homeViewModel.onShowBottomSheet(bottomSheet)
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
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (btnAccount, bottomActions, codeLogo) = createRefs()
                Image(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(vertical = 15.dp)
                        .padding(horizontal = 15.dp)
                        .constrainAs(codeLogo) {
                            start.linkTo(parent.start)
                            top.linkTo(parent.top)
                        },
                    painter = painterResource(
                        R.drawable.ic_code_logo_white
                    ),
                    contentDescription = "",
                )

                Image(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(vertical = 10.dp)
                        .padding(horizontal = 15.dp)
                        .constrainAs(btnAccount) {
                            end.linkTo(parent.end)
                            top.linkTo(parent.top)
                        }
                        .clip(CircleShape)
                        .clickable {
                            showBottomSheet(HomeBottomSheet.ACCOUNT)
                        },
                    painter = painterResource(
                        R.drawable.ic_home_options
                    ),
                    contentDescription = "",
                )

                HomeBottom(
                    modifier = Modifier
                        .constrainAs(bottomActions) {
                            bottom.linkTo(parent.bottom)
                        }
                        .padding(bottom = 16.dp),
                    onPress = {
                        showBottomSheet(it)
                    },
                    viewModel = homeViewModel,
                )
            }
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
                payloadData = dataState.billPayloadData ?: listOf(),
                onClose = {
                    billVisibleState.targetState = false
                }
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
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 30.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    if (!dataState.isReceiveDialogVisible) {
                        Row(
                            modifier = Modifier
                                .background(Gray50, RoundedCornerShape(30.dp))
                                .clip(RoundedCornerShape(30.dp))
                                .clickable {
                                    if (!dataState.isRemoteSendLoading) homeViewModel.onRemoteSend(
                                        context
                                    )
                                }
                                .padding(vertical = 15.dp, horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                Row(
                                    modifier = Modifier.alpha(if (!dataState.isRemoteSendLoading) 1f else 0f)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_remote_send),
                                        contentDescription = "",
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(
                                        modifier = Modifier.padding(start = 10.dp),
                                        text = stringResource(R.string.action_send)
                                    )
                                }

                                if (dataState.isRemoteSendLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Center)
                                    )
                                }
                            }

                        }

                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Row(
                        modifier = Modifier
                            .background(Gray50, RoundedCornerShape(30.dp))
                            .clip(RoundedCornerShape(30.dp))
                            .clickable { homeViewModel.hideBill(isSent = false, isVibrate = false) }
                            .padding(vertical = 15.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_bill_close),
                            contentDescription = "",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            modifier = Modifier.padding(start = 10.dp),
                            text = stringResource(R.string.action_cancel)
                        )
                    }
                }
            }
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
    val onClose = { hideBottomSheet() }
    GiveKinSheet(giveKinSheetState, isGiveKinSheetOpen, onClose)

    //Balance sheet modal
    ModalSheetLayout(
        balanceSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideBottomSheet() }
        BalanceSheet(isBalanceSheetOpen, onClose) {
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
            AccountSheet(isAccountSheetOpen, homeViewModel = homeViewModel, onCloseQuickly)
        }
    }

    //FAQ sheet modal
    ModalSheetLayout(
        faqSheetState
    ) {
        Box(modifier = Modifier.padding(vertical = 1.dp)) {}
        val onClose = { hideSheet(HomeBottomSheet.FAQ) }
        if (isFaqSheetOpen) {
            HomeFaqSheet(isFaqSheetOpen) {
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
            GetKinSheet(isGetKinSheetOpen, homeViewModel, onClose)
        }
    }
}
