package xyz.flipchat.app.data

import androidx.compose.ui.graphics.Color
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.ui.utils.generateComplementaryColorPalette

data class RoomInfo(
    val id: ID? = null,
    val number: Long = 0,
    val title: String = "",
    val imageUrl: String? = null,
    val memberCount: Int = 0,
    val hostId: ID? = null,
    val hostName: String? = null,
    val roomNumber: Long = 0,
    val messagingFee: Kin = Kin.fromQuarks(0),
) {
    val customTitle: String = runCatching { Regex("^#\\d+:\\s*(.*)").find(title)?.groupValues?.get(1).orEmpty() }.getOrDefault("")

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
