package com.flipcash.app.scanner.internal

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.scanner.internal.bills.BillContainer
import com.flipcash.app.session.LocalSessionController
import com.flipcash.features.scanner.R
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.orZero
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.CodeScanner
import com.getcode.ui.scanner.NoCamerasAvailableException
import com.getcode.ui.utils.KeepScreenOn
import com.getcode.util.vibration.LocalVibrator
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.implementation.KikCodeResult
import dev.theolm.rinku.DeepLink
import timber.log.Timber
import com.flipcash.app.core.extensions.navigateTo
import com.flipcash.app.core.navigation.DeeplinkType

@Composable
internal fun Scanner() {
    val router = LocalRouter.current!!
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.current!!
    val state by session.state.collectAsState()
    val billState by session.billState.collectAsState()
    val analytics = rememberAnalytics()

    var isPaused by remember { mutableStateOf(false) }

    var previewing by remember {
        mutableStateOf<Boolean?>(null)
    }

    var cameraAvailable by remember {
        mutableStateOf(true)
    }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val biometricsState = LocalBiometricsState.current

    val vibrator = LocalVibrator.current

    LaunchedEffect(biometricsState, previewing) {
        if (previewing == true) {
            focusManager.clearFocus()
        }

        if (!biometricsState.passed) return@LaunchedEffect

        if (previewing != null) {
            session.onCameraScanning(previewing!!)
        }
    }

    @SuppressLint("LocalContextGetResourceValueCall")
    BillContainer(
        isPaused = isPaused,
        onAction = {
            when (it) {
                ScannerDecorItem.Give -> {
                    // only allow navigation to give when there is something to give
                    val hasBalance = state.giveableBalance.orZero().isPositive
                    if (!hasBalance) {
                        BottomBarManager.showAlert(
                            title = context.getString(R.string.title_noBalanceYet),
                            message = context.getString(R.string.description_noBalanceYet),
                        )
                        return@BillContainer
                    }
                }
                else -> Unit
            }
            navigator.navigateTo(it.screen)
        },
        scannerView = {
            CodeScanner(
                scanningEnabled = previewing == true,
                cameraGesturesEnabled = true,
                onPreviewStateChanged = {
                    cameraAvailable = true
                    previewing = it
                },
                onCodeScanned = { result ->
                    when (result) {
                        is CodeScanResult.QrCode -> {
                            val urls = result.results
                            val deeplink = urls.firstNotNullOfOrNull { url ->
                                val type = router.classify(DeepLink(url))
                                analytics.deeplinkParsed(type, url)
                                type
                            }
                            if (deeplink != null) {
                                vibrator.vibrate(duration = 50)
                                when (deeplink) {
                                    is DeeplinkType.CashLink -> {
                                        session.openCashLink(deeplink.entropy)
                                    }
                                    is DeeplinkType.Navigatable -> {
                                        val routes = when (deeplink) {
                                            is DeeplinkType.TokenInfo -> listOf(
                                                com.flipcash.app.core.AppRoute.Sheets.Wallet,
                                                com.flipcash.app.core.AppRoute.Token.Info(deeplink.mint, fromDeeplink = true)
                                            )
                                            else -> emptyList()
                                        }
                                        if (routes.isNotEmpty()) {
                                            navigator.navigateTo(routes)
                                        }
                                    }
                                    is DeeplinkType.Login -> Unit
                                }
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

    LaunchedEffect(navigator.backStack.size) {
        previewing = navigator.backStack.size <= 1
    }

    LaunchedEffect(billState.bill) {
        if (billState.bill != null) {
            navigator.hide()
        }
    }

    KeepScreenOn(
        isEnabled = billState.bill != null,
        useBrightness = true,
    )
}
