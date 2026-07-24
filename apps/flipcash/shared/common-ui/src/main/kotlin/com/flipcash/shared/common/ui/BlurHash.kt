package com.flipcash.shared.common.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Decoder for [BlurHash](https://blurha.sh) strings — the compact, blurred image preview that
 * ships in a media item's [com.flipcash.services.models.chat.ImageMetadata]. Decode it into a tiny
 * bitmap and let the image layer scale it up as an instant placeholder while the real (larger)
 * image downloads.
 *
 * Ported from the public-domain reference implementation. A hash only encodes a handful of DCT
 * components, so decode at a low resolution (a couple dozen pixels) — anything larger just wastes
 * cycles for no visible gain.
 */
object BlurHash {

    /**
     * Decodes [blurHash] into an [width] × [height] [Bitmap], or null if the string is malformed.
     * [punch] adjusts contrast (1f = as encoded).
     */
    fun decode(blurHash: String?, width: Int, height: Int, punch: Float = 1f): Bitmap? {
        if (blurHash == null || blurHash.length < 6 || width <= 0 || height <= 0) return null

        val sizeFlag = decode83(blurHash, 0, 1) ?: return null
        val numCompX = sizeFlag % 9 + 1
        val numCompY = sizeFlag / 9 + 1
        if (blurHash.length != 4 + 2 * numCompX * numCompY) return null

        val maxAc = ((decode83(blurHash, 1, 2) ?: return null) + 1) / 166f
        val colors = Array(numCompX * numCompY) { i ->
            if (i == 0) {
                decodeDc(decode83(blurHash, 2, 6) ?: return null)
            } else {
                val from = 4 + i * 2
                decodeAc(decode83(blurHash, from, from + 2) ?: return null, maxAc * punch)
            }
        }
        return composeBitmap(width, height, numCompX, numCompY, colors)
    }

    private fun decode83(str: String, from: Int, to: Int): Int? {
        var result = 0
        for (i in from until to) {
            val index = CHARS.indexOf(str[i])
            if (index < 0) return null
            result = result * 83 + index
        }
        return result
    }

    private fun decodeDc(colorEnc: Int): FloatArray = floatArrayOf(
        srgbToLinear(colorEnc shr 16 and 255),
        srgbToLinear(colorEnc shr 8 and 255),
        srgbToLinear(colorEnc and 255),
    )

    private fun decodeAc(value: Int, maxAc: Float): FloatArray = floatArrayOf(
        signPow((value / (19 * 19) - 9) / 9f) * maxAc,
        signPow((value / 19 % 19 - 9) / 9f) * maxAc,
        signPow((value % 19 - 9) / 9f) * maxAc,
    )

    /** sign(value) * value² — the reference impl's quantisation curve. */
    private fun signPow(value: Float): Float = value.pow(2f).withSign(value)

    private fun srgbToLinear(colorEnc: Int): Float {
        val v = colorEnc / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt()
    }

    private fun composeBitmap(
        width: Int,
        height: Int,
        numCompX: Int,
        numCompY: Int,
        colors: Array<FloatArray>,
    ): Bitmap {
        // Precompute the cosine basis for each axis so the inner pixel loop is just multiplies.
        val cosX = FloatArray(width * numCompX)
        for (x in 0 until width) {
            for (i in 0 until numCompX) {
                cosX[x * numCompX + i] = cos(PI * x * i / width).toFloat()
            }
        }
        val cosY = FloatArray(height * numCompY)
        for (y in 0 until height) {
            for (j in 0 until numCompY) {
                cosY[y * numCompY + j] = cos(PI * y * j / height).toFloat()
            }
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numCompY) {
                    val cy = cosY[y * numCompY + j]
                    for (i in 0 until numCompX) {
                        val basis = cosX[x * numCompX + i] * cy
                        val color = colors[j * numCompX + i]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }
                pixels[y * width + x] =
                    (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private const val CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"
}

/**
 * Remembers a [Painter] that draws [blurHash]'s decoded preview, or null when the hash is absent or
 * malformed. Decodes at a deliberately tiny [width]/[height]; callers scale it to fit.
 */
@Composable
fun rememberBlurHashPainter(
    blurHash: String?,
    width: Int = 24,
    height: Int = 24,
): Painter? = remember(blurHash, width, height) {
    val bitmap = BlurHash.decode(blurHash, width, height) ?: return@remember null
    BitmapPainter(bitmap.asImageBitmap())
}
