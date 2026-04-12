package com.flipcash.app.bill.customization

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import com.flipcash.app.bill.customization.internal.features.BlendMode
import com.flipcash.app.bill.customization.internal.features.ColorState
import com.flipcash.app.bill.customization.internal.features.GraphicState
import com.flipcash.app.bill.customization.models.PlaygroundFeature
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.BillTexture
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.toAGColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface BillPlaygroundController {
    val state: StateFlow<PlaygroundState>
    val canUndo: Boolean
    val canCopy: Boolean

    fun dispatchEvent(event: Event)
    fun cancel()
}

data class PlaygroundState(
    val bill: Bill? = null,
    val features: List<PlaygroundFeature> = PlaygroundFeature.entries.toList(),
    val selectedFeature: PlaygroundFeature = PlaygroundFeature.Background,
    val backgroundState: ColorState = ColorState(),
    val textureState: GraphicState = GraphicState(),
) {
    val isCustomizing: Boolean
        get() = bill != null

    val background: BillBackground
        get() {
            return with(backgroundState) {
                if (selectedColors.count() > 1) {
                    BillBackground.Gradient.from(
                        selectedColors.map { it.color.toAGColor() }
                    )
                } else {
                    BillBackground.Solid.from(selectedColors.first().color.toAGColor())
                }
            }
        }

    val texture: BillTexture?
        get() {
            if (!features.contains(PlaygroundFeature.Textures)) return null
            return with (textureState) {
                val selectedTexture = selectedOption ?: return null
                BillTexture(
                    index = selectedTexture + 1,
                    blendMode = when (selectedBlendMode.blendMode) {
                        BlendMode.Normal -> com.getcode.opencode.model.ui.BlendMode.Normal
                        BlendMode.Lighten -> com.getcode.opencode.model.ui.BlendMode.Lighten
                        BlendMode.Screen -> com.getcode.opencode.model.ui.BlendMode.Screen
                        BlendMode.ColorDodge -> com.getcode.opencode.model.ui.BlendMode.ColorDodge
                        BlendMode.PlusLighter -> com.getcode.opencode.model.ui.BlendMode.PlusLighter
                    },
                    strength = selectedBlendMode.strength
                )
            }
        }

    val customizations: TokenBillCustomizations
        get() = TokenBillCustomizations(
            background = background,
            texture = texture,
            icon = null,
        )

    val customizedBill: Bill?
        get() {
            if (bill == null) return null
            if (bill !is Bill.Cash) return null
            return bill.copy(
                token = bill.token.copy(
                    billCustomizations = customizations
                )
            )
        }

    val backgroundBrush: Brush
        get() {
            with (backgroundState) {
                if (selectedColors.size == 1) return Brush.verticalGradient(listOf(selectedColors.first().color, selectedColors.first().color))
                val colorStops = selectedColors.mapIndexed { index, store -> index.toFloat() / (selectedColors.size - 1) to store.color }
                return Brush.verticalGradient(
                    colorStops = colorStops.toTypedArray()
                )
            }
        }
}

sealed interface Event {
    data class Load(
        val customizations: TokenBillCustomizations?,
        val amount: Fiat = 5.toFiat(),
    ): Event
    data class SelectFeature(val feature: PlaygroundFeature): Event
    sealed interface Colors: Event {
        data object AddSlot : Colors
        data object RemoveSlot : Colors
        data class SelectSlot(val slot: Int) : Colors
        data class PreviewColorChange(val hsv: Hsv) : Colors
        data class CommitColorChange(val hsv: Hsv) : Colors
        data class LoadBackground(val background: BillBackground) : Colors
        data object OpenHueControls : Colors
        data object CloseHueControls : Colors
    }

    sealed interface Graphics: Event {
        data class ApplyGraphic(val resource: Int) : Graphics
        data class CommitBlendMode(val blendMode: BlendMode, val strength: Float? = null): Graphics
        data class PreviewBlendMode(val strength: Float = 1f): Graphics
    }
    data object Undo: Event
    data object Copy: Event
}

internal object StubPlaygroundController : BillPlaygroundController {
    override val state: StateFlow<PlaygroundState> = MutableStateFlow(PlaygroundState())
    override val canUndo: Boolean = false
    override val canCopy: Boolean = false
    override fun dispatchEvent(event: Event) = Unit
    override fun cancel() = Unit
}

val LocalBillPlaygroundController =
    staticCompositionLocalOf<BillPlaygroundController> { StubPlaygroundController }