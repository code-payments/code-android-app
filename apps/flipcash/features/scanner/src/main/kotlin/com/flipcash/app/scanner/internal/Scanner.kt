package com.flipcash.app.scanner.internal

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.AppRoute.Token.*
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.scanner.internal.bills.ScannableContainer
import com.flipcash.app.session.CodeScanEvent
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.TipCardEvent
import com.flipcash.features.scanner.R
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.libs.qr.StaticQrAnalyzer
import com.getcode.manager.TopBarManager
import com.getcode.media.StaticImageAnalyzerImpl
import com.getcode.media.StaticImageResult
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.scanner.CodeScanner
import com.getcode.ui.scanner.NoCamerasAvailableException
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.KeepScreenOn
import com.getcode.util.vibration.LocalVibrator
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.implementation.KikCodeAnalyzer
import com.kik.kikx.kikcodes.implementation.KikCodeResult
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun Scanner() {
    val router = LocalRouter.current!!
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.current!!
    val billState by session.billState.collectAsStateWithLifecycle()
    val analytics = rememberAnalytics()

    var previewing by remember {
        mutableStateOf<Boolean?>(null)
    }

    var cameraAvailable by remember {
        mutableStateOf(true)
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    val biometricsState = LocalBiometricsState.current

    val vibrator = LocalVibrator.current
    val scope = rememberCoroutineScope()

    // Built by hand for the same reason `rememberMultiCodeAnalyzer` builds the camera's: nothing
    // binds these types, and neither the module that declares them nor `:ui:scanner` is
    // annotation-processed, so there is no graph to ask.
    val kikCodeAnalyzer = remember(context) {
        val scanner = KikCodeScannerImpl()
        KikCodeAnalyzer(scanner, StaticImageAnalyzerImpl(context.applicationContext, scanner))
    }
    val staticQrAnalyzer = remember(context) { StaticQrAnalyzer(context.applicationContext) }

    var isScanningStillImage by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    // A grab started from a picked photo, still waiting on the server. The camera raises
    // `CashCodeNotLive` too and is meant to ignore it, so the flag is what separates the two.
    var awaitingStillImageGrab by remember { mutableStateOf(false) }

    val onDeeplinkScanned = { deeplink: DeeplinkType ->
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
                    navigator.navigateAll(routes)
                }
            }
            is DeeplinkType.Login -> Unit
            is DeeplinkType.Tipcard -> {
                session.resolveTipCard(TipCardOwner.ById(deeplink.userId))
            }
            // A printed or on-screen `flipcash.com/{username}` is the
            // same card as a scanned `flipcash.com/{id}`, addressed by
            // handle.
            is DeeplinkType.TipcardByUsername -> {
                session.resolveTipCard(TipCardOwner.ByUsername(deeplink.username))
            }
        }
    }

    // Stated once and by name. The branches above still refuse credential routes on their own,
    // but that refusal used to be an emergent property of two `when`s in different places.
    val firstScannableDeeplink = { urls: List<String> ->
        urls.firstNotNullOfOrNull { url ->
            val type = router.classify(DeepLink(url))
            analytics.deeplinkParsed(type, url)
            type
        }?.takeIf { it.isScannable }
    }

    val showNoCodeFound = {
        TopBarManager.showMessage(
            title = resources.getString(R.string.error_title_noCodeInPhoto),
            message = resources.getString(R.string.error_description_noCodeInPhoto),
        )
    }

    val onImagePicked = { uri: Uri ->
        scanJob?.cancel()
        scanJob = scope.launch {
            isScanningStillImage = true
            try {
                when (val result = kikCodeAnalyzer.detect(uri)) {
                    is StaticImageResult.Found -> {
                        // No dedup to get past: `CodeScanDelegate` suppresses a rendezvous only
                        // once a grab has succeeded, and clears it again on failure, so picking
                        // the same photo twice is refused for the same reason pointing the camera
                        // at a spent code is.
                        awaitingStillImageGrab = true
                        session.onCodeScan(result.code)
                    }
                    // A QR route that fails `isScannable` reads as "no code": someone sent a login
                    // link learns nothing from being told why it was refused.
                    StaticImageResult.NotFound,
                    StaticImageResult.Exhausted,
                    -> {
                        val deeplink = firstScannableDeeplink(staticQrAnalyzer.detect(uri))
                        if (deeplink != null) onDeeplinkScanned(deeplink) else showNoCodeFound()
                    }
                }
            } finally {
                isScanningStillImage = false
            }
        }
        Unit
    }

    // Scanning your own tip card resolves to nothing to pay, so send the user to the You tab —
    // the surface that owns their card — rather than leaving the scan with no visible outcome.
    // Covers both scan shapes (QR tip link and OpenCode tip payload); they share the guard in
    // TipCardDelegate that raises this. The equivalent deeplink is handled in AppRouter.
    LaunchedEffect(session, navigator) {
        session.tipCardEvents.collect { event ->
            when (event) {
                TipCardEvent.OwnCardScanned ->
                    navigator.navigateAll(listOf(AppRoute.Sheets.Menu))
            }
        }
    }

    LaunchedEffect(session) {
        session.codeScanEvents.collect { event ->
            when (event) {
                CodeScanEvent.CashCodeNotLive -> {
                    if (awaitingStillImageGrab) {
                        awaitingStillImageGrab = false
                        TopBarManager.showMessage(
                            title = resources.getString(R.string.error_title_cashCodeNotLive),
                            message = resources.getString(R.string.error_description_cashCodeNotLive),
                        )
                    }
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
        @SuppressLint("LocalContextGetResourceValueCall")
        ScannableContainer(
            onImagePicked = onImagePicked,
            scannerView = {
                CodeScanner(
                    scanningEnabled = previewing == true,
                    cameraGesturesEnabled = true,
                    onPinchStateChanged = { _, _ -> },
                    onPreviewStateChanged = {
                        cameraAvailable = true
                        previewing = it
                    },
                    onCodeScanned = { result ->
                        when (result) {
                            is CodeScanResult.QrCode -> {
                                firstScannableDeeplink(result.results)?.let(onDeeplinkScanned)
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

        if (isScanningStillImage) {
            StillImageScanOverlay(onCancel = { scanJob?.cancel() })
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
            // The grab landed, so there is nothing left to explain.
            awaitingStillImageGrab = false
            navigator.hide()
        }
    }

    // One instance rather than two: a second `KeepScreenOn` leaving composition clears the window
    // flag the first one set. Brightness is boosted only for a presented bill, which is the case
    // it exists for — a still-image search shows nothing that has to be scannable.
    KeepScreenOn(
        isEnabled = billState.bill != null || isScanningStillImage,
        useBrightness = billState.bill != null,
    )
}

/**
 * Covers the scanner while a picked image is searched.
 *
 * Blocking on purpose: the ladder runs for seconds, and a camera the user can still drive
 * underneath a search they cannot see the end of is two scanners at once.
 */
@Composable
private fun StillImageScanOverlay(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black50)
            .noRippleClickable { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
        ) {
            CodeCircularProgressIndicator()
            CodeButton(
                text = stringResource(R.string.action_cancel),
                buttonState = ButtonState.Filled50,
                onClick = onCancel,
            )
        }
    }
}
