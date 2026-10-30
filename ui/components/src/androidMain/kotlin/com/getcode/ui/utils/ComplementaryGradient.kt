package com.getcode.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.getcode.model.ID
import com.getcode.utils.sha512

private val resultMap: MutableMap<ID, Triple<Color, Color, Color>> = mutableMapOf()

fun generateComplementaryColorPalette(identifier: ID): Triple<Color, Color, Color>? {
    val hash = runCatching { identifier.toByteArray().sha512() }.getOrNull() ?: return null
    return resultMap.getOrPutIfNonNull(identifier) {
        generateGradientColors(hash.toList())
    }
}

private fun generateGradientColors(bytes: List<Byte>): Triple<Color, Color, Color> {
    // Calculate relative luminance for contrast ratio
    fun Color.contrastRatioWithWhite(): Float {
        val whiteLuminance = 1f // White's relative luminance is 1
        val colorLuminance = this.luminance()
        return if (whiteLuminance > colorLuminance) {
            (whiteLuminance + 0.05f) / (colorLuminance + 0.05f)
        } else {
            (colorLuminance + 0.05f) / (whiteLuminance + 0.05f)
        }
    }

    // Adjust color to meet contrast requirements
    fun Color.ensureReadableWithWhite(): Color {
        var adjustedColor = this
        var attempts = 0
        // WCAG AA requires contrast ratio of 4.5:1 for normal text
        while (adjustedColor.contrastRatioWithWhite() < 4.5f && attempts < 10) {
            // Darken the color by reducing its value in HSV
            adjustedColor = Color.hsv(
                hue = adjustedColor.hue,
                saturation = adjustedColor.saturation,
                value = (adjustedColor.value() * 0.9f).coerceIn(0f, 1f)
            )
            attempts++
        }
        return adjustedColor
    }

    // Generate base colors as before
    val hue = (bytes.take(3).fold(0) { acc, byte ->
        acc + byte.toUByte().toInt()
    } % 360).toFloat()

    val saturation = 0.75f
    val baseValue = 0.85f
    val valueVariation = (bytes[3].toUByte().toInt() % 10) / 100f
    val hueShift = 20f

    fun boundValue(value: Float) = value.coerceIn(0f, 1f)

    val startColor = Color.hsv(
        hue = hue,
        saturation = boundValue(saturation),
        value = boundValue(baseValue - valueVariation)
    )

    val middleColor = Color.hsv(
        hue = (hue + hueShift) % 360f,
        saturation = boundValue(saturation * 0.95f),
        value = boundValue(baseValue)
    )

    // Generate end color and ensure it meets contrast requirements
    val endColor = Color.hsv(
        hue = (hue + hueShift * 2) % 360f,
        saturation = boundValue(saturation * 0.9f),
        value = boundValue(baseValue + valueVariation)
    ).ensureReadableWithWhite()

    return Triple(startColor, middleColor, endColor)
}