package com.flipcash.shared.common.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.asImage

internal actual fun argbToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

internal actual fun ImageBitmap.asCoilImage(): coil3.Image = asAndroidBitmap().asImage()

/** Decodes [blurHash] into an Android [Bitmap]; kept for Android callers of the old API. */
fun BlurHash.decode(blurHash: String?, width: Int, height: Int, punch: Float = 1f): Bitmap? =
    decodeImage(blurHash, width, height, punch)?.asAndroidBitmap()
