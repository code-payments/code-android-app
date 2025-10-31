package com.flipcash.app.bill.customization

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.Token
import com.getcode.ui.utils.hexToColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface BillPlaygroundController {
    val state: StateFlow<State>
    val canUndo: Boolean
    fun customizeFor(token: Token)

    fun dispatchEvent(event: Event)
    fun cancel()
}

enum class PlaygroundMode {
    ColorPanel, Presets
}

data class ColorStore(
    val committed: Color,
    val transition: Color? = null,
) {
    constructor(color: Color): this (color, null)
    constructor(color: String): this (hexToColor(color))

    val color: Color
        get() = transition ?: committed
}

data class State(
    val bill: Bill? = null,
    val presetsOpen: Boolean = false,
    val mode: PlaygroundMode = PlaygroundMode.Presets,
    val selectedSlot: Int = 0,
    val maxSlots: Int = MaxGradientColors,
    val selectedColors: List<ColorStore> = buildGradient(),
    val colorOptions: List<BillBackground.Solid> = PresetColorOptions,
    val gradientOptions: List<BillBackground.Gradient> = PresetGradients,
) {
    val isCustomizing: Boolean
        get() = bill != null

    val brush: Brush
        get() {
            if (selectedColors.size == 1) return Brush.verticalGradient(listOf(selectedColors.first().color, selectedColors.first().color))
            val colorStops = selectedColors.mapIndexed { index, store -> index.toFloat() / (selectedColors.size - 1) to store.color }
            return Brush.verticalGradient(
                colorStops = colorStops.toTypedArray()
            )
        }
}

sealed interface Event {
    data object AddSlot: Event
    data object RemoveSlot: Event
    data class SelectSlot(val slot: Int): Event
    data class PreviewColorChange(val color: Color): Event
    data class CommitColorChange(val color: Color): Event
    data class LoadBackground(val background: BillBackground): Event
    data object OpenHueControls: Event
    data object CloseHueControls: Event
    data object Undo: Event
}

private const val MaxGradientColors = 3

@OptIn(ExperimentalStdlibApi::class)
private val PresetColorOptions: List<BillBackground.Solid> = listOf(
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

private val PresetGradients: List<BillBackground.Gradient> = listOf(
    BillBackground.Gradient(listOf("#FFE2EAF3", "#FF5487C1")),
    BillBackground.Gradient(listOf("#FFCDB3FF", "#FFECE0E5", "#FFFB9655")),
    BillBackground.Gradient(listOf("#FFFFD5E7", "#FF31D9AA")),
    BillBackground.Gradient(listOf("#FFE4307B", "#FF6123FF", "#FF8A02CE")),
    BillBackground.Gradient(listOf("#FFCCCC31", "#FFC65A24")),
    BillBackground.Gradient(listOf("#FF4F63FC", "#FF31D9AA"))
)

private fun buildGradient(): List<ColorStore> {
    val swatches = PresetColorOptions

    // return a random 3 color gradient
    return listOf(
        ColorStore(swatches.random().colorHex),
        ColorStore(swatches.random().colorHex),
        ColorStore(swatches.random().colorHex),
    )
}

internal object StubPlaygroundController : BillPlaygroundController {
    override val state: StateFlow<State> = MutableStateFlow(State())
    override val canUndo: Boolean = false
    override fun customizeFor(token: Token) = Unit
    override fun dispatchEvent(event: Event) = Unit
    override fun cancel() = Unit
}

val LocalBillPlaygroundController =
    staticCompositionLocalOf<BillPlaygroundController> { StubPlaygroundController }