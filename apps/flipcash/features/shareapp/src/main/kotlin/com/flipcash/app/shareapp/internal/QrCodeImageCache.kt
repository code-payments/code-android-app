package com.flipcash.app.shareapp.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter

object QrCodeImageCache {
    var downloadQrCode by mutableStateOf<BitmapPainter?>(null)
}