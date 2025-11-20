package com.flipcash.app.bill.customization.models

import androidx.compose.ui.graphics.Color
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.color
import com.getcode.ui.utils.hexToColor
import com.getcode.ui.utils.hsv
import java.util.UUID

data class ColorStore(
    val committed: Hsv,
    val transition: Hsv? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    constructor(color: Color): this (color.hsv, null)
    constructor(color: String): this (hexToColor(color))

    val hsv: Hsv
        get() = transition ?: committed

    val color: Color
        get() = hsv.color
}