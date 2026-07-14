package com.flipcash.app.bill.customization.internal

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundContext
import com.flipcash.app.bill.customization.PlaygroundState
import com.flipcash.app.bill.customization.internal.defaults.PresetTextures
import com.flipcash.app.bill.customization.internal.features.BackgroundController
import com.flipcash.app.bill.customization.internal.features.ColorState
import com.flipcash.app.bill.customization.internal.features.GraphicState
import com.flipcash.app.bill.customization.internal.features.TextureController
import com.flipcash.app.bill.customization.models.PlaygroundFeature
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.featureflags.FeatureFlagController
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.BlendMode
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.opencode.utils.nonce
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.toHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.flipcash.app.bill.customization.Event.Colors as ColorEvent
import com.flipcash.app.bill.customization.Event.Graphics as GraphicsEvent
import com.flipcash.app.bill.customization.internal.features.BlendMode as UiBlendMode

@OptIn(ExperimentalStdlibApi::class)
class InternalBillPlaygroundController(
    private val clipboard: ClipboardManager,
    featureFlags: FeatureFlagController,
) : BillPlaygroundController, ViewModel() {

    private val backgroundController = BackgroundController { pushUndoSnapshot() }
    private val textureController = TextureController(featureFlags, viewModelScope) { pushUndoSnapshot() }

    private val _state: MutableStateFlow<PlaygroundState> = MutableStateFlow(PlaygroundState())
    override val state: StateFlow<PlaygroundState> = _state.asStateFlow()

    private val json = Json { explicitNulls = false }

    init {
        combine(
            _state,
            backgroundController.state,
            textureController.state,
        ) { currentPlayground, currentBackground, currentTexture ->
            val features = currentPlayground.context.availableFeatures
                .filter { feature ->
                    when (feature) {
                        PlaygroundFeature.Textures -> currentTexture.enabled
                        else -> true
                    }
                }
            val selectedFeature = currentPlayground.selectedFeature
                .takeIf { it in features }
                ?: features.firstOrNull()
                ?: currentPlayground.selectedFeature

            currentPlayground.copy(
                backgroundState = currentBackground,
                textureState = currentTexture,
                features = features,
                selectedFeature = selectedFeature,
            )
        }.onEach { combinedState ->
            _state.value = combinedState
        }.launchIn(viewModelScope)
    }

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    private val undoStack = ArrayDeque<PlaygroundState>()

    private fun pushUndoSnapshot() {
        val cleanSnapshot = _state.value.copy(
            backgroundState = backgroundController.getCurrentCleanState(),
            textureState = textureController.getCurrentCleanState(),
        )

        undoStack.addLast(cleanSnapshot)
    }

    override val canUndo: Boolean
        get() = !undoStack.isEmpty()

    override val canCopy: Boolean
        get() = false

    override val canPaste: Boolean
        get() = true

    private fun customizeFor(
        token: Token,
        amount: Fiat,
        customizations: TokenBillCustomizations?,
        context: PlaygroundContext,
    ) {
        // create amount for the bill
        val demoAmount = LocalFiat.fromUsd(usdf = amount)

        // provide bill "data" to render the scan code
        val payloadInfo = OpenCodePayload(
            kind = PayloadKind.MultiMintCash,
            value = demoAmount.nativeAmount,
            nonce = nonce
        )
        // create bill for token
        val bill = Bill.Cash(
            token = token.copy(billCustomizations = customizations),
            amount = demoAmount,
            disableGestures = true,
            data = payloadInfo.codeData.toList(),
            renderAsBill = true,
        )

        _state.update { current ->
            current.copy(
                bill = bill,
                context = context,
            )
        }
    }

    override fun dispatchEvent(event: Event) {
        when (event) {
            // high level actions
            is Event.Load -> {
                // Background: apply the saved background if one exists, otherwise
                // fall back to a fresh (random) default. Restoring the random
                // ColorState() default on *every* Load is what caused the bill to
                // flicker through random colors on entry: Load is re-dispatched
                // whenever the playground echoes its customizations back to the
                // screen, so each pass re-rolled buildGradient() and repainted the
                // bill before the real customization was applied.
                val savedBackground = event.customizations?.background
                if (savedBackground != null) {
                    backgroundController.load(savedBackground)
                } else {
                    backgroundController.restore(ColorState())
                }

                // Textures carry no random default, so resetting them each Load is safe.
                textureController.restore(
                    GraphicState(
                        enabled = textureController.state.value.enabled,
                        options = PresetTextures,
                        selectedOption = 0
                    )
                )

                event.customizations?.texture?.let { texture ->
                    textureController.apply(texture.index - 1) // 1-based → 0-based
                    val mode = when (texture.blendMode) {
                        BlendMode.Normal -> UiBlendMode.Normal
                        BlendMode.Lighten -> UiBlendMode.Lighten
                        BlendMode.Screen -> UiBlendMode.Screen
                        BlendMode.ColorDodge -> UiBlendMode.ColorDodge
                        BlendMode.PlusLighter -> UiBlendMode.PlusLighter
                    }
                    textureController.commitBlend(mode, texture.strength)
                }

                // Clear undo — fresh session (also clears entries pushed by load/apply/commitBlend)
                undoStack.clear()

                customizeFor(Token.usdf, event.amount, event.customizations, event.context)
            }

            is Event.PresentPasteOption -> {
                _state.update { it.copy(awaitingPaste = event.show) }
            }
            is Event.ApplyFromClipboard -> {
                applyConfiguration()
                dispatchEvent(Event.PresentPasteOption(false))
            }
            
            // selecting feature from tab row
            is Event.SelectFeature -> selectFeature(event.feature)
            Event.Copy -> copyConfiguration()
            Event.Undo -> undoLastChange()

            is ColorEvent -> {
                when (event) {
                    ColorEvent.AddSlot -> addSlot()
                    ColorEvent.CloseHueControls -> closeHueControls()
                    is ColorEvent.CommitColorChange -> commitColorChangeForSlot(event.hsv)
                    is ColorEvent.LoadBackground -> loadBackground(event.background)
                    ColorEvent.OpenHueControls -> openHueControls()
                    is ColorEvent.PreviewColorChange -> previewColorChangeForSlot(event.hsv)
                    ColorEvent.RemoveSlot -> removeSlot()
                    is ColorEvent.SelectSlot -> selectSlot(event.slot)
                }
            }

            is GraphicsEvent -> {
                when (event) {
                    is GraphicsEvent.ApplyGraphic -> applyGraphic(event.resource)
                    is GraphicsEvent.CommitBlendMode -> commitBlend(event.blendMode, event.strength)
                    is GraphicsEvent.PreviewBlendMode -> previewBlend(event.strength)
                }
            }
        }
    }

    private fun selectFeature(feature: PlaygroundFeature) {
        _state.update {
            it.copy(selectedFeature = feature)
        }
    }

    private fun addSlot() {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.addSlot()
            }
        }
    }

    private fun removeSlot() {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.removeSlot()
            }
        }
    }

    private fun selectSlot(slot: Int) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.selectSlot(slot)
            }
        }
    }

    private fun commitColorChangeForSlot(hsv: Hsv) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.commitColorChangeForSlot(hsv)
            }
        }
    }

    private fun previewColorChangeForSlot(hsv: Hsv) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.previewColorChangeForSlot(hsv)
            }
        }
    }

    private fun openHueControls() {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background -> {
                backgroundController.openHueControls()
            }
        }
    }

    private fun closeHueControls() {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Colorable) return

        when (feature) {
            PlaygroundFeature.Background ->  backgroundController.closeHueControls()
        }
    }

    private fun loadBackground(background: BillBackground) {
        backgroundController.load(background)
    }

    private fun applyGraphic(resource: Int) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Graphic) return

        when (feature) {
            PlaygroundFeature.Textures -> textureController.apply(resource)
        }
    }

    private fun commitBlend(blendMode: UiBlendMode, strength: Float?) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Graphic) return

        when (feature) {
            PlaygroundFeature.Textures -> textureController.commitBlend(blendMode, strength)
        }
    }

    private fun previewBlend(strength: Float) {
        val feature = _state.value.selectedFeature
        if (feature !is PlaygroundFeature.Graphic) return

        when (feature) {
            PlaygroundFeature.Textures -> textureController.previewBlend(strength)
        }
    }

    private fun undoLastChange() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeLast()
            applyStateSnapshot(previous)
        }
    }

    private fun applyStateSnapshot(snapshot: PlaygroundState) {
        // Restore each sub-controller to the exact state from the snapshot
        backgroundController.restore(snapshot.backgroundState)
        textureController.restore(snapshot.textureState)

        // Finally emit the merged state
        _state.update {
            it.copy(
                backgroundState = backgroundController.getCurrentCleanState(),
                textureState = textureController.getCurrentCleanState(),
            )
        }
    }

    private fun applyConfiguration() {
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: return
        try {
            // Try new JSON format first
            val root = Json.parseToJsonElement(text).jsonObject

            val backgroundArray = root["background"]?.jsonArray ?: return
            val hexColors = backgroundArray.map { it.jsonPrimitive.content }
            val background = if (hexColors.size == 1) {
                BillBackground.Solid(hexColors.first())
            } else {
                BillBackground.Gradient(hexColors)
            }
            backgroundController.load(background)

            if (textureController.state.value.enabled) {
                root["texture"]?.jsonObject?.let { tex ->
                    val index = tex["index"]?.jsonPrimitive?.int ?: return@let
                    val blendModeName = tex["blendMode"]?.jsonPrimitive?.content ?: return@let
                    val strength = tex["strength"]?.jsonPrimitive?.float ?: return@let

                    val blendMode =
                        UiBlendMode.entries.firstOrNull { it.name == blendModeName } ?: return@let
                    textureController.apply(index - 1)
                    textureController.commitBlend(blendMode, strength)
                }
            }
        } catch (_: Exception) {
            // Fall back to legacy comma-separated hex colors
            val hexColors = text.split(",").map { it.trim() }.filter { it.startsWith("#") }
            if (hexColors.isEmpty()) return
            val background = if (hexColors.size == 1) {
                BillBackground.Solid(hexColors.first())
            } else {
                BillBackground.Gradient(hexColors)
            }
            backgroundController.load(background)
        }
    }

    private fun copyConfiguration() {
        val payload = buildJsonObject {
            putJsonArray("background") {
                _state.value.backgroundState.selectedColors.forEach { store ->
                    add(store.color.toHex())
                }
            }
            _state.value.texture?.let { texture ->
                putJsonObject("texture") {
                    put("index", texture.index)
                    put("blendMode", texture.blendMode.name)
                    put("strength", texture.strength)
                }
            }
        }

        val export = json.encodeToString(payload)

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "",
                export
            )
        )
    }

    override fun cancel() {
        undoStack.clear()
        _state.update { PlaygroundState() }
    }
}