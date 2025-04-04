package com.flipcash.app

import androidx.compose.runtime.Composable
import com.getcode.ui.scanner.CodeScanner
import dev.bmcreations.tipkit.engines.TipsEngine

@Composable
fun App(
    tipsEngine: TipsEngine,
) {
    CodeScanner(
        scanningEnabled = true,
        cameraGesturesEnabled = true,
        invertedDragZoomEnabled = true,
        onPreviewStateChanged = {},
        onCodeScanned = { code ->

        }
    )
}