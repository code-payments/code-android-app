package com.getcode.ui.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

data class Rgb(val r: Float, val g: Float, val b: Float)
data class Hls(val h: Float, val l: Float, val s: Float)

val Color.hue: Float
    get() = colorToHls(this).h

val Color.hls: Hls
    get() = colorToHls(this)

val Color.rgb: Rgb
    get() = colorToRgb(this)

fun Color.blendWith(other: Color, alpha: Float): Color {
    return blendColors(this, other, alpha)
}

val Color.greyscale: Color
    get() = toGrayscale(this)

val Color.inverted: Color
    get() = invertColor(this)

val Hls.rgb: Rgb
    get() = hlsToRgb(this)

val Hls.color: Color
    get() = hlsToColor(this)

val Rgb.hls: Hls
    get() = rgbToHls(this)

val Rgb.color: Color
    get() = rgbToHls(this).color


private fun colorToRgb(color: Color): Rgb {
    return Rgb(
        r = (color.red * 255),
        g = (color.green * 255),
        b = (color.blue * 255),
    )
}

private fun colorToHls(color: Color): Hls {
    return rgbToHls(color.red, color.green, color.blue)
}

fun rgbToHls(r: Float, g: Float, b: Float): Hls {
    return rgbToHls(Rgb(r, g, b))
}

private fun rgbToHls(rgb: Rgb): Hls {
    val (r, g, b) = rgb
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2
    val s =
        if (max == min) 0f else if (l < 0.5f) (max - min) / (max + min) else (max - min) / (2 - max - min)
    val h = when (max) {
        min -> 0f
        r -> 60f * (((g - b) / (max - min)) % 6)
        g -> 60f * (((b - r) / (max - min)) + 2)
        b -> 60f * (((r - g) / (max - min)) + 4)
        else -> 0f
    }
    return Hls((h + 360) % 360, l, s)
}

fun hlsToRgb(h: Float, l: Float, s: Float): Rgb {
    return hlsToRgb(Hls(h, l, s))
}

private fun hlsToRgb(hls: Hls): Rgb {
    val (h, _, _) = hls
    val (c, x, _) = hlsToComponents(hls)

    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Rgb(r, g, b)
}


private fun hlsToComponents(hls: Hls): Triple<Float, Float, Float> {
    val (h, l, s) = hls

    val c = (1f - abs(2 * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2 - 1f))
    val m = l - c / 2f

    return Triple(c.coerceIn(0f, 1f), x.coerceIn(0f, 1f), m.coerceIn(0f, 1f))
}


private fun hlsToColor(h: Float, l: Float, s: Float): Color {
    return hlsToColor(Hls(h, l, s))
}

private fun hlsToColor(hls: Hls): Color {
    val (_, _, m) = hlsToComponents(hls)
    val (r, g, b) = hlsToRgb(hls)

    return Color(
        red = r + m,
        green = g + m,
        blue = b + m,
        alpha = 1f
    )
}

private fun invertColor(color: Color): Color {
    return Color(
        red = 1f - color.red,
        green = 1f - color.green,
        blue = 1f - color.blue,
        alpha = color.alpha
    )
}

fun adjustBrightness(color: Color, factor: Float): Color {
    val red = (color.red * 255).roundToInt()
    val green = (color.green * 255).roundToInt()
    val blue = (color.blue * 255).roundToInt()

    val newRed = (red + (255 - red) * factor).coerceIn(0f, 255f).roundToInt()
    val newGreen = (green + (255 - green) * factor).coerceIn(0f, 255f).roundToInt()
    val newBlue = (blue + (255 - blue) * factor).coerceIn(0f, 255f).roundToInt()

    return Color(newRed / 255f, newGreen / 255f, newBlue / 255f)
}

fun deriveTargetColor(sourceColor: Color, targetLightness: Float, targetSaturation: Float): Color {
    val hls = colorToHls(sourceColor)
    val newHue = hls.h
    val newLightness = (hls.l + targetLightness).coerceIn(0f, 1f) // Increase lightness
    val newSaturation = (hls.s + targetSaturation).coerceIn(0f, 1f) // Increase saturation

    return hlsToColor(newHue, newLightness, newSaturation)
}

fun deriveTargetColor(sourceColor: Color, hls: Hls): Color {
    val newHue = sourceColor.hue
    val newLightness = (hls.l + hls.l).coerceIn(0f, 1f) // Increase lightness
    val newSaturation = (hls.s + hls.s).coerceIn(0f, 1f) // Increase saturation

    return hlsToColor(newHue, newLightness, newSaturation)
}

fun adjustColor(sourceColor: Color, alpha: Float): List<Color> {
    val brighterColor = adjustBrightness(sourceColor, 0.8f)
    val darkerColor = adjustBrightness(sourceColor, 0.3f)

    return listOf(
        blendColors(sourceColor, brighterColor, alpha),
        blendColors(sourceColor, darkerColor, alpha)
    )
}

private fun blendColors(sourceColor: Color, targetColor: Color, alpha: Float): Color {
    val blendedRed = (1 - alpha) * sourceColor.red + alpha * targetColor.red
    val blendedGreen = (1 - alpha) * sourceColor.green + alpha * targetColor.green
    val blendedBlue = (1 - alpha) * sourceColor.blue + alpha * targetColor.blue

    return Color(blendedRed, blendedGreen, blendedBlue, alpha)
}

private fun toGrayscale(color: Color): Color {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    // Ensure the new luminance is within the valid range [0, 1]
    return Color(luminance.toFloat(), luminance.toFloat(), luminance.toFloat(), color.alpha)
}
