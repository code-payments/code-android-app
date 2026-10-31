package com.flipcash.app.blob

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/**
 * Encoder for [BlurHash](https://blurha.sh) strings, the counterpart of the decoder in
 * `shared/common-ui`. A sealed photo can't be previewed from the server, so its hash is computed
 * here from the plaintext and travels inside the encrypted message.
 *
 * Works on an ARGB [IntArray]; the platform only supplies the thumbnail, through [ImageCodec].
 */
object BlurHashEncoder {

    private const val THUMBNAIL_EDGE = 64

    /**
     * Hash of [pixels] (row-major ARGB, alpha ignored) using [componentsX] × [componentsY] DCT
     * components. Returns "" when the input is unusable; a missing preview must never fail a send.
     */
    fun encode(pixels: IntArray, width: Int, height: Int, componentsX: Int, componentsY: Int): String {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return ""
        if (componentsX !in 1..9 || componentsY !in 1..9) return ""

        val factors = Array(componentsX * componentsY) { index ->
            factor(pixels, width, height, index % componentsX, index / componentsX)
        }
        val dc = factors[0]
        val ac = factors.drop(1)

        val hash = StringBuilder()
        hash.append(encode83((componentsX - 1) + (componentsY - 1) * 9, 1))

        val maxValue: Float
        if (ac.isNotEmpty()) {
            val actualMax = ac.maxOf { channels -> channels.maxOf { abs(it) } }
            val quantisedMax = floor(actualMax * 166f - 0.5f).toInt().coerceIn(0, 82)
            maxValue = (quantisedMax + 1) / 166f
            hash.append(encode83(quantisedMax, 1))
        } else {
            maxValue = 1f
            hash.append(encode83(0, 1))
        }

        hash.append(encode83(encodeDc(dc), 4))
        for (factor in ac) hash.append(encode83(encodeAc(factor, maxValue), 2))
        return hash.toString()
    }

    /**
     * Hash of the photo [thumbnail] was taken from: 4x3 components for landscape (and square) and
     * 3x4 for portrait, judged by the photo's own size. "" on any failure.
     */
    fun fromThumbnail(thumbnail: ArgbThumbnail): String =
        runCatching {
            val landscape = thumbnail.sourceWidth >= thumbnail.sourceHeight
            encode(thumbnail.pixels, thumbnail.width, thumbnail.height, if (landscape) 4 else 3, if (landscape) 3 else 4)
        }.getOrDefault("")

    /** Hash of [jpeg] read through [codec]'s [THUMBNAIL_EDGE]-px thumbnail; null if it won't decode or hash. */
    fun fromJpeg(codec: ImageCodec, jpeg: ByteArray): String? =
        codec.thumbnail(jpeg, THUMBNAIL_EDGE)?.let(::fromThumbnail)?.takeIf { it.isNotEmpty() }

    private fun factor(pixels: IntArray, width: Int, height: Int, i: Int, j: Int): FloatArray {
        var r = 0f
        var g = 0f
        var b = 0f
        val normalisation = if (i == 0 && j == 0) 1f else 2f
        for (y in 0 until height) {
            val basisY = cos(PI * j * y / height).toFloat()
            for (x in 0 until width) {
                val basis = normalisation * cos(PI * i * x / width).toFloat() * basisY
                val pixel = pixels[y * width + x]
                r += basis * srgbToLinear(pixel shr 16 and 255)
                g += basis * srgbToLinear(pixel shr 8 and 255)
                b += basis * srgbToLinear(pixel and 255)
            }
        }
        val scale = 1f / (width * height)
        return floatArrayOf(r * scale, g * scale, b * scale)
    }

    private fun encodeDc(value: FloatArray): Int =
        (linearToSrgb(value[0]) shl 16) or (linearToSrgb(value[1]) shl 8) or linearToSrgb(value[2])

    private fun encodeAc(value: FloatArray, maxValue: Float): Int {
        fun quantise(v: Float): Int =
            floor(signPow(v / maxValue, 0.5f) * 9f + 9.5f).toInt().coerceIn(0, 18)
        return quantise(value[0]) * 19 * 19 + quantise(value[1]) * 19 + quantise(value[2])
    }

    private fun signPow(value: Float, exp: Float): Float = abs(value).pow(exp) * sign(value)

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt()
    }

    private fun encode83(value: Int, length: Int): String {
        val out = CharArray(length)
        var remaining = value
        for (i in length - 1 downTo 0) {
            out[i] = CHARS[remaining % 83]
            remaining /= 83
        }
        return String(out)
    }

    private const val CHARS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"
}
