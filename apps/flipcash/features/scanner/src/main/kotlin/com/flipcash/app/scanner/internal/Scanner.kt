package com.flipcash.app.scanner.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.scanner.internal.bills.BillContainer
import com.flipcash.app.session.LocalSessionController
import com.flipcash.features.scanner.R
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.orZero
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.CodeScanner
import com.getcode.ui.scanner.NoCamerasAvailableException
import com.getcode.util.vibration.LocalVibrator
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.implementation.KikCodeResult
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.Timer
import kotlin.concurrent.schedule

@Composable
internal fun Scanner(deepLink: DeeplinkType?) {
    val router = LocalRouter.currentOrThrow
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.currentOrThrow
    val state by session.state.collectAsState()
    val billState by session.billState.collectAsState()

    val sheetLifecycleHandler = rememberSheetAutoResign()
    LaunchedEffect(sheetLifecycleHandler) {
        sheetLifecycleHandler.handle()
    }

    var isPaused by remember { mutableStateOf(false) }

    var previewing by remember {
        mutableStateOf<Boolean?>(null)
    }

    var cameraStarted by remember {
        mutableStateOf(state.autoStartCamera == true)
    }

    var cameraAvailable by remember {
        mutableStateOf(true)
    }

    val context = LocalContext.current

    var deepLinkSaved by remember(deepLink) {
        mutableStateOf(deepLink)
    }

    val vibrator = LocalVibrator.current

    ScannerDeepLinkHandler(
        deepLink = deepLinkSaved,
        previewing = previewing,
        session = session,
        navigator = navigator
    ) {
        deepLinkSaved = null
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    BillContainer(
        isPaused = isPaused,
        isCameraReady = previewing == true,
        isCameraStarted = cameraStarted,
        onStartCamera = { cameraStarted = true },
        onAction = {
            when (it) {
                ScannerDecorItem.Give -> {
                    // only allow navigation to give when there is something to give
                    val hasBalance = state.giveableBalance.orZero().isPositive
                    if (!hasBalance) {
                        BottomBarManager.showError(
                            title = context.getString(R.string.title_noBalanceYet),
                            message = context.getString(R.string.description_noBalanceYet),
                        )
                        return@BillContainer
                    }
                }
                else -> Unit
            }
            navigator.show(ScreenRegistry.get(it.screen))
        },
        scannerView = {
            CodeScanner(
                scanningEnabled = previewing == true,
                cameraGesturesEnabled = true,
                invertedDragZoomEnabled = true,
                onPreviewStateChanged = {
                    cameraAvailable = true
                    previewing = it
                },
                onCodeScanned = { result ->
                    when (result) {
                        is CodeScanResult.QrCode -> {
                            val urls = result.results
                            val deeplink = urls.firstNotNullOfOrNull { url ->
                                router.processType(DeepLink(url))
                            }
                            println("deeplink type = $deeplink")
                            if (deeplink != null) {
                                vibrator.vibrate(duration = 50)
                                deepLinkSaved = deeplink
                            }
                        }
                        is KikCodeResult -> {
                            session.onCodeScan(result.kikCode)
                        }
                    }
                },
                onError = {
                    if (it is NoCamerasAvailableException) {
                        cameraAvailable = false
                    }
                    ErrorUtils.handleError(it)
                }
            )
        },
    )

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                Timber.d("onStart")
                isPaused = false
            }

            Lifecycle.Event.ON_STOP -> {
                Timber.d("onStop")
                if (state.autoStartCamera == false) {
                    cameraStarted = false
                }
            }

            Lifecycle.Event.ON_PAUSE -> {
                Timber.d("onPause")
                isPaused = true
            }

            Lifecycle.Event.ON_RESUME -> {
                Timber.d("onResume")
                isPaused = false
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

    LaunchedEffect(billState.bill) {
        if (billState.bill != null) {
            navigator.hide()
        }
        resetScreenTimeout(context as Activity)
    }
}

private fun resetScreenTimeout(activity: Activity) {
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    Timer().schedule(10000) {
        activity.runOnUiThread {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}