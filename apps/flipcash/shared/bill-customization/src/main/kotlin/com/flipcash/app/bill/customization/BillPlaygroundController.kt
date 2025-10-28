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
    fun customizeFor(token: Token)

    fun dispatchEvent(event: Event)
    fun cancel()
}

data class State(
    val bill: Bill? = null,
    val hueControlsOpen: Boolean = false,
    val selectedSlot: Int = 0,
    val maxSlots: Int = MaxGradientColors,
    val selectedColors: List<Color> = DefaultColorOptions.filterIsInstance<BillBackground.Gradient>()
        .random().colors.map { hexToColor(it) },
    val colorOptions: List<BillBackground> = DefaultColorOptions
) {
    val isCustomizing: Boolean
        get() = bill != null

    val brush: Brush
        get() {
            if (selectedColors.size == 1) return Brush.verticalGradient(listOf(selectedColors.first(), selectedColors.first()))
            val colorStops = selectedColors.mapIndexed { index, color -> index.toFloat() / (selectedColors.size - 1) to color }
            return Brush.verticalGradient(
                colorStops = colorStops.toTypedArray()
            )
        }
}

sealed interface Event {
    data object AddSlot: Event
    data object RemoveSlot: Event
    data class SelectSlot(val slot: Int): Event
    data class ChangeColor(val color: Color): Event
    data class LoadBackground(val background: BillBackground): Event
    data object OpenHueControls: Event
    data object CloseHueControls: Event
}

private const val MaxGradientColors = 3

@OptIn(ExperimentalStdlibApi::class)
private val DefaultColorOptions = listOf(
    BillBackground.Solid("#FFFFFFFF"),
    BillBackground.Solid("#FF000000"),
    BillBackground.Solid("#FFFF453A"),
    BillBackground.Solid("#FFFF9F0A"), // Orange
    BillBackground.Solid("#FFFFD60A"),
    BillBackground.Solid("#FF30D158"),
    BillBackground.Gradient(listOf("#FFE2EAF3", "#FF5487C1")),
    BillBackground.Gradient(listOf("#FFCDB3FF", "#FFECE0E5", "#FFFB9655")),
    BillBackground.Gradient(listOf("#FFFFD5E7", "#FF31D9AA")),
    BillBackground.Gradient(listOf("#FFE4307B", "#FF6123FF", "#FF8A02CE")),
    BillBackground.Gradient(listOf("#FFCCCC31", "#FFC65A24")),
    BillBackground.Gradient(listOf("#FF4F63FC", "#FF31D9AA"))
)

internal object StubPlaygroundController : BillPlaygroundController {
    override val state: StateFlow<State> = MutableStateFlow(State())

    override fun customizeFor(token: Token) = Unit
    override fun dispatchEvent(event: Event) = Unit
    override fun cancel() = Unit
}

val LocalBillPlaygroundController =
    staticCompositionLocalOf<BillPlaygroundController> { StubPlaygroundController }