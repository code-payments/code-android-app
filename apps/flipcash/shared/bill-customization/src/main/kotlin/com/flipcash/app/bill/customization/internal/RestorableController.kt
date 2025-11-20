package com.flipcash.app.bill.customization.internal

import com.flipcash.app.bill.customization.internal.features.BlendMode
import com.flipcash.app.bill.customization.internal.features.ColorState
import com.flipcash.app.bill.customization.internal.features.GraphicState
import com.getcode.ui.utils.Hsv
import kotlinx.coroutines.flow.StateFlow

interface RestorableController<T> {
    fun getCurrentCleanState(): T           // no transient preview values
    fun restore(state: T)                   // idempotent, full replace
}

interface RestorableColorController : RestorableController<ColorState> {
    val state: StateFlow<ColorState>
    fun addSlot()
    fun removeSlot()
    fun selectSlot(slot: Int)
    fun commitColorChangeForSlot(hsv: Hsv)
    fun previewColorChangeForSlot(hsv: Hsv)
    fun openHueControls()
    fun closeHueControls()
}

interface RestorableGraphicController: RestorableController<GraphicState> {
    val state: StateFlow<GraphicState>

    fun apply(texture: Int)
    fun commitBlend(mode: BlendMode, strength: Float?)
    fun previewBlend(strength: Float)
}