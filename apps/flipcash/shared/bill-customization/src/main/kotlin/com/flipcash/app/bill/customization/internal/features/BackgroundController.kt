package com.flipcash.app.bill.customization.internal.features

import com.flipcash.app.bill.customization.internal.RestorableColorController
import com.flipcash.app.bill.customization.internal.defaults.MaxGradientColors
import com.flipcash.app.bill.customization.internal.defaults.PresetColorOptions
import com.flipcash.app.bill.customization.internal.defaults.PresetGradients
import com.flipcash.app.bill.customization.internal.defaults.buildGradient
import com.flipcash.app.bill.customization.models.ColorPlaygroundMode
import com.flipcash.app.bill.customization.models.ColorStore
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.hexToColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal class BackgroundController @Inject constructor(
    private val onBeforeMutation: () -> Unit,
): RestorableColorController {

    private val _state = MutableStateFlow(ColorState())

    override val state: StateFlow<ColorState>
        get() = _state.asStateFlow()

    override fun getCurrentCleanState(): ColorState = ColorState(
        mode = _state.value.mode,
        maxSlots = _state.value.maxSlots,
        selectedColors = _state.value.selectedColors.map { it.copy(transition = null) },
        selectedSlot = _state.value.selectedSlot,
        colorOptions = _state.value.colorOptions,
        gradientOptions = _state.value.gradientOptions,
    )

    override fun restore(state: ColorState) {
        _state.value = state
    }

    override fun addSlot() {
        if (_state.value.selectedColors.count() < _state.value.maxSlots) {
            onBeforeMutation()
            _state.updateWithHistory { state ->
                val lastSlotColor = state.selectedColors[state.selectedSlot]
                val insertIndex = state.selectedSlot + 1
                val colors = state.selectedColors.toMutableList().apply {
                    add(insertIndex, ColorStore(lastSlotColor.color))
                }.toList()

                state.copy(
                    selectedColors = colors,
                    selectedSlot = insertIndex,
                )
            }
        }
    }

    override fun removeSlot() {
        if (_state.value.selectedColors.count() > 1) {
            onBeforeMutation()
            _state.updateWithHistory { state ->
                val indexToRemove = state.selectedSlot
                val newColors = state.selectedColors.toMutableList().apply { removeAt(indexToRemove) }
                state.copy(
                    selectedColors = newColors,
                    selectedSlot = state.selectedSlot.coerceAtMost(newColors.size - 1),
                )
            }
        }
    }

    override fun selectSlot(slot: Int) {
        _state.update { s ->
            s.copy(selectedSlot = slot)
        }
    }

    override fun commitColorChangeForSlot(hsv: Hsv) {
        onBeforeMutation()
        _state.updateWithHistory { state ->
            val slotIndex = state.selectedSlot
            val updatedColors = state.selectedColors.toMutableList().apply {
                set(slotIndex, ColorStore(hsv))
            }.toList()

            state.copy(selectedColors = updatedColors,)
        }
    }

    override fun previewColorChangeForSlot(hsv: Hsv) {
        // No history push—treat as transient
        _state.update { s ->
            val slotIndex = s.selectedSlot
            val slot = s.selectedColors[slotIndex]
            val updatedColors = s.selectedColors.toMutableList().apply {
                set(slotIndex, slot.copy(transition = hsv))
            }.toList()
            s.copy(selectedColors = updatedColors)
        }
    }

    override fun openHueControls() {
        _state.update { s ->
            s.copy(mode = ColorPlaygroundMode.ColorPanel)
        }
    }

    override fun closeHueControls() {
        _state.update { s ->
            s.copy(mode = ColorPlaygroundMode.Presets)
        }
    }

    fun load(billBackground: BillBackground) {
        when (billBackground) {
            is BillBackground.Gradient -> {
                val colors = billBackground.colors.map { hexToColor(it) }
                onBeforeMutation()
                _state.updateWithHistory { state ->
                    state.copy(
                        selectedColors = colors.map { ColorStore(it) },
                        selectedSlot = colors.lastIndex
                    )
                }
            }

            is BillBackground.Solid -> {
                onBeforeMutation()
                _state.updateWithHistory { state ->
                    state.copy(
                        selectedColors = listOf(ColorStore(billBackground.colorHex)),
                        selectedSlot = 0
                    )
                }
            }
        }
    }

    private inline fun MutableStateFlow<ColorState>.updateWithHistory(block: (ColorState) -> ColorState) {
        update { block(getCurrentCleanState()) }
    }
}

data class ColorState(
    val mode: ColorPlaygroundMode = ColorPlaygroundMode.Presets,
    val selectedSlot: Int = 0,
    val maxSlots: Int = MaxGradientColors,
    val selectedColors: List<ColorStore> = buildGradient(),
    val colorOptions: List<BillBackground.Solid> = PresetColorOptions,
    val gradientOptions: List<BillBackground.Gradient> = PresetGradients,
)