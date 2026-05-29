package com.flipcash.app.bill.customization.internal.features

import com.flipcash.app.bill.customization.internal.RestorableGraphicController
import com.flipcash.app.bill.customization.internal.defaults.PresetTextures
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import com.flipcash.app.bill.customization.models.BlendStore
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.shared.bill.customizations.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal class TextureController @Inject constructor(
    featureFlags: FeatureFlagController,
    scope: CoroutineScope,
    private val onBeforeMutation: () -> Unit,
) : RestorableGraphicController {

    private val _state = MutableStateFlow(
        GraphicState(
            options = PresetTextures,
            selectedOption = 0,
        )
    )

    init {
        featureFlags.observe(FeatureFlag.BillTextures)
            .onEach { enabled -> _state.update { it.copy(enabled = enabled) } }
            .launchIn(scope)
    }

    override val state: StateFlow<GraphicState>
        get() = _state.asStateFlow()

    override fun getCurrentCleanState(): GraphicState = GraphicState(
        options = PresetTextures,
        selectedOption = _state.value.selectedOption,
        selectedBlendMode = _state.value.selectedBlendMode,
        blendModes = _state.value.blendModes,
    )

    override fun restore(state: GraphicState) {
        _state.value = state
    }

    override fun apply(texture: Int) {
        onBeforeMutation()
        _state.update {
            it.copy(selectedOption = texture)
        }
    }

    override fun commitBlend(mode: BlendMode, strength: Float?) {
        onBeforeMutation()
        _state.update {
            val previousStrength = it.selectedBlendMode.strength
            val newStrength = strength ?: previousStrength
            it.copy(selectedBlendMode = BlendStore(blendMode = mode, committedStrength = newStrength))
        }
    }

    override fun previewBlend(strength: Float) {
        _state.update {
            val currentBlendMode = it.selectedBlendMode
            it.copy(
                selectedBlendMode = currentBlendMode.copy(temporaryStrength = strength)
            )
        }
    }
}

data class GraphicState(
    val enabled: Boolean = false,
    val options: List<Int> = emptyList(),
    val blendModes: List<BlendStore> = BlendMode.entries.map {
        BlendStore(
            blendMode = it,
            committedStrength = 0.5f
        )
    }.toList(),
    val selectedOption: Int? = null,
    val selectedBlendMode: BlendStore = blendModes.first(),
)

enum class BlendMode(
    val labelRes: Int,
) {
    Normal(R.string.label_blendModeNormal),
    Lighten(R.string.label_blendModeLighten),
    Screen(R.string.label_blendModeScreen),
    ColorDodge(R.string.label_blendModeColorDodge),
    PlusLighter(R.string.label_blendModePlusLighter),
    ;
}

