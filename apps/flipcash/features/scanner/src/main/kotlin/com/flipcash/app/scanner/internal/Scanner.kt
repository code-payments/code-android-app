package com.flipcash.app.scanner.internal

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.AppRoute.Token.*
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.scanner.internal.bills.ScannableContainer
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.TipCardEvent
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.navigation.core.LocalCodeNavigator
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

@Composable
internal fun Scanner() {
    val router = LocalRouter.current!!
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.current!!
    val state by session.state.collectAsStateWithLifecycle()
    val billState by session.billState.collectAsStateWithLifecycle()
    val analytics = rememberAnalytics()
    val isNewUi by LocalFeatureFlags.current.observe(FeatureFlag.NewUi)
        .collectAsStateWithLifecycle()

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

    var isPinching by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    // Scanning your own tip card resolves to nothing to pay, so send the user to the You tab —
    // the surface that owns their card — rather than leaving the scan with no visible outcome.
    // Covers both scan shapes (QR tip link and OpenCode tip payload); they share the guard in
    // TipCardDelegate that raises this. The equivalent deeplink is handled in AppRouter.
    LaunchedEffect(session, navigator, isNewUi) {
        session.tipCardEvents.collect { event ->
            when (event) {
                TipCardEvent.OwnCardScanned ->
                    navigator.navigateAll(listOf(AppRoute.Sheets.Menu), isNewUi = isNewUi)
            }
        }
    }

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
    ScannableContainer(
        isPaused = isPaused,
        isPinching = isPinching,
        zoomRatio = zoomRatio,
        onAction = {
            when (it) {
                ScannerDecorItem.Give -> {
                    // only allow navigation to give when there is something to give
                    // Zero balance -> prompt to add money; otherwise the user has funds
                    // but nothing giveable -> prompt to discover a currency.
                    // presentDepositOptions picks the right prompt based on balance.
                    if (!state.hasGiveableBalance) {
                        session.presentDepositOptions { route ->
                            navigator.openAsSheet(route)
                        }

                        return@ScannableContainer
                    }
                }
                else -> Unit
            }
            navigator.openAsSheet(it.screen)
        },
        scannerView = {
            CodeScanner(
                scanningEnabled = previewing == true,
                cameraGesturesEnabled = true,
                onPinchStateChanged = { pinching, zoom ->
                    isPinching = pinching
                    zoomRatio = zoom
                },
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
                                        val routes: List<AppRoute> = when (deeplink) {
                                            is DeeplinkType.TokenInfo -> listOf(
                                                AppRoute.Sheets.Wallet,
                                                Info(deeplink.mint, fromDeeplink = true)
                                            )
                                            // Scanned tip-DM code — same destination as the
                                            // /tip/chat/{id} deeplink.
                                            is DeeplinkType.TipChat -> listOf(
                                                AppRoute.Sheets.Tips(),
                                                AppRoute.Messaging.Chat(deeplink.identifier),
                                            )
                                            else -> emptyList()
                                        }
                                        if (routes.isNotEmpty()) {
                                            navigator.navigateAll(routes, isNewUi = isNewUi)
                                        }
                                    }
                                    is DeeplinkType.Login -> Unit
                                    is DeeplinkType.Tipcard -> {
                                        session.resolveTipCard(TipCardOwner.ById(deeplink.userId))
                                    }
                                    // A printed or on-screen `flipcash.com/{username}` is the
                                    // same card as a scanned /tip/{id}, addressed by handle.
                                    is DeeplinkType.TipcardByUsername -> {
                                        session.resolveTipCard(TipCardOwner.ByUsername(deeplink.username))
                                    }
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
