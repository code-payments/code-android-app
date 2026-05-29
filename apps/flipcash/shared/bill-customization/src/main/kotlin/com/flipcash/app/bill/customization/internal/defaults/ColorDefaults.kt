package com.flipcash.app.bill.customization.internal.defaults

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import com.flipcash.app.bill.customization.models.ColorStore
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.color
import com.getcode.ui.utils.hsv


internal const val MaxGradientColors = 3

@OptIn(ExperimentalStdlibApi::class)
internal val PresetColorOptions: List<BillBackground.Solid> = listOf(
    BillBackground.Solid("#FFFF453A"), // Red
    BillBackground.Solid("#FFFF9F0A"), // Orange
    BillBackground.Solid("#FFFFD60A"), // Yellow
    BillBackground.Solid("#FF30D158"), // Green
    BillBackground.Solid("#FF00FFE9"), // Cyan
    BillBackground.Solid("#FF0054FF"), // Blue
    BillBackground.Solid("#FFCDB3FF"), // Mauve
    BillBackground.Solid("#FFFF1493"), // Hot Pink
    BillBackground.Solid("#FF00D4FF"), // Cyan Blue
    BillBackground.Solid("#FFFB9655"), // Light Salmon
    BillBackground.Solid("#FF009688"), // Teal
    BillBackground.Solid("#FF8B4513"), // Brown
)

internal val PresetGradients: List<BillBackground.Gradient> = listOf(
    BillBackground.Gradient(listOf("#FFE2EAF3", "#FF5487C1")),
    BillBackground.Gradient(listOf("#FFCDB3FF", "#FFECE0E5", "#FFFB9655")),
    BillBackground.Gradient(listOf("#FFFFD5E7", "#FF31D9AA")),
    BillBackground.Gradient(listOf("#FFE4307B", "#FF6123FF", "#FF8A02CE")),
    BillBackground.Gradient(listOf("#FFCCCC31", "#FFC65A24")),
    BillBackground.Gradient(listOf("#FF4F63FC", "#FF31D9AA"))
)

internal fun buildGradient(): List<ColorStore> {
    val swatches = PresetColorOptions

    // select a random base
    val base = Color(swatches.random().colorHex.toColorInt())

    // mutate per recipe
    val top = base.hsv.copy(s = 0.53f, v = 1f,)
    val mid = base.hsv.copy(s = 1f, v = 0.71f,)
    val bottom = base.hsv.copy(s = 1f, v = 0.23f,)

    // return the random hue gradient
    return listOf(
        ColorStore(top.color),
        ColorStore(mid.color),
        ColorStore(bottom.color),
    )
}