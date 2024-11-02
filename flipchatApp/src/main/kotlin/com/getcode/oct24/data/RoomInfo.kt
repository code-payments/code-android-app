package com.getcode.oct24.data

import androidx.compose.ui.graphics.Color
import com.getcode.model.ID
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.utils.sha512

data class RoomInfo(
    val id: ID? = null,
    val number: Long = 0,
    val title: String = "",
    val memberCount: Int = 0,
    val hostName: String? = null,
    val coverCharge: Long? = null,
) {
    companion object {
        val DEFAULT_GRADIENT_SAMPLE = Triple(
            Color(0xFFFFBB00),
            Color(0xFF7306B7),
            Color(0xFF3E32C4),
        )
    }

    val gradientColors: Triple<Color, Color, Color>
        get() {
            return id?.let { generateComplementaryColorPalette(it) } ?: DEFAULT_GRADIENT_SAMPLE
        }
}
