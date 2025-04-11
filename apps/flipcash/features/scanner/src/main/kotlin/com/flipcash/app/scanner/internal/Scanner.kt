package com.flipcash.app.scanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.core.LocalSessionController
import com.flipcash.app.scanner.internal.bills.BillContainer
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.CodeScanner
import com.getcode.utils.ErrorUtils
import timber.log.Timber

@Composable
internal fun Scanner() {
    val navigator = LocalCodeNavigator.current
    val session = LocalSessionController.currentOrThrow
    val state by session.state.collectAsState()
    var isPaused by remember { mutableStateOf(false) }

    var previewing by remember {
        mutableStateOf(false)
    }

    var cameraStarted by remember {
        mutableStateOf(state.autoStartCamera == true)
    }

    LaunchedEffect(previewing) {
        session.onCameraScanning(previewing)
    }

    BillContainer(
        isPaused = isPaused,
        isCameraReady = previewing,
        isCameraStarted = cameraStarted,
        onStartCamera = { cameraStarted = true },
        onAction = {
            navigator.show(ScreenRegistry.get(it.screen))
        },
        scannerView = {
            CodeScanner(
                scanningEnabled = previewing,
                cameraGesturesEnabled = true,
                invertedDragZoomEnabled = true,
                onPreviewStateChanged = { previewing = it },
                onCodeScanned = {
                    if (previewing) {
                        session.onCodeScan(it)
                    }
                },
                onError = { ErrorUtils.handleError(it) }
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
//                session.startSheetDismissTimer {
//                    Timber.d("hiding from timeout")
//                    navigator.hide()
//                }
            }

            Lifecycle.Event.ON_RESUME -> {
                Timber.d("onResume")
                isPaused = false
//                session.stopSheetDismissTimer()
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

    LaunchedEffect(state.billState.bill) {
        if (state.billState.bill != null) {
            navigator.hide()
        }
//        session.resetScreenTimeout(context as Activity)
    }
}