package com.flipcash.app.bill.customization.internal

import com.flipcash.app.bill.customization.ColorStore
import com.flipcash.app.bill.customization.MaxGradientColors
import com.flipcash.app.bill.customization.PlaygroundMode
import com.flipcash.app.bill.customization.PresetColorOptions
import com.flipcash.app.bill.customization.PresetGradients
import com.flipcash.app.bill.customization.State
import com.flipcash.app.bill.customization.buildGradient
import com.getcode.opencode.model.financial.BillBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class BackgroundController @Inject constructor() {

    private val _state = MutableStateFlow(ColorState())
    val state: StateFlow<ColorState>
        get() = _state.asStateFlow()


}

data class ColorState(
    val mode: PlaygroundMode = PlaygroundMode.Presets,
    val selectedSlot: Int = 0,
    val maxSlots: Int = MaxGradientColors,
    val selectedColors: List<ColorStore> = buildGradient(),
    val colorOptions: List<BillBackground.Solid> = PresetColorOptions,
    val gradientOptions: List<BillBackground.Gradient> = PresetGradients,
)