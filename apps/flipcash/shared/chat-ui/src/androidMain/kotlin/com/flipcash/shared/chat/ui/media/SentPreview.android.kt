package com.flipcash.shared.chat.ui.media

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** The longest edge a sent preview is decoded at; the bubble never draws larger. */
private const val PREVIEW_MAX_EDGE = 1080

internal fun decodeSentPreview(jpeg: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_MAX_EDGE) sample *= 2
    BitmapFactory.decodeByteArray(
        jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample },
    )?.also { it.prepareToDraw() }?.asImageBitmap()
}.getOrNull()
