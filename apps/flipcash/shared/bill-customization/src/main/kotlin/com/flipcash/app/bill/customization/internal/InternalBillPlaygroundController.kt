package com.flipcash.app.bill.customization.internal

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundContext
import com.flipcash.app.bill.customization.PlaygroundState
import com.flipcash.app.bill.customization.internal.features.BackgroundController
import com.flipcash.app.bill.customization.internal.features.BlendMode
import com.flipcash.app.bill.customization.internal.features.TextureController
import com.flipcash.app.bill.customization.models.PlaygroundFeature
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.ui.BillBackground
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
import com.flipcash.app.bill.customization.Event.Colors as ColorEvent
import com.flipcash.app.bill.customization.Event.Graphics as GraphicsEvent

@OptIn(ExperimentalStdlibApi::class)
class InternalBillPlaygroundController(
    private val clipboard: ClipboardManager,
) : BillPlaygroundController, ViewModel() {

    private val backgroundController = BackgroundController { pushUndoSnapshot() }
    private val textureController = TextureController { pushUndoSnapshot() }

    private val _state: MutableStateFlow<PlaygroundState> = MutableStateFlow(PlaygroundState())
    override val state: StateFlow<PlaygroundState> = _state.asStateFlow()

    init {
        combine(
            _state,
            backgroundController.state,
            textureController.state,
        ) { currentPlayground, currentBackground, currentTexture ->
            currentPlayground.copy(
                backgroundState = currentBackground,
                textureState = currentTexture,
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

    private fun customizeFor(
        token: Token,
        amount: Fiat,
        customizations: TokenBillCustomizations?,
        context: PlaygroundContext,
    ) {
        // create amount for the bill
        val demoAmount = LocalFiat(
            usdf = amount,
        )

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
            data = payloadInfo.codeData.toList()
        )

        _state.update { current ->
            val features = context.availableFeatures
            val selectedFeature = current.selectedFeature.takeIf { it in features }
                ?: features.first()
            current.copy(
                bill = bill,
                context = context,
                features = features,
                selectedFeature = selectedFeature,
            )
        }
    }

    override fun dispatchEvent(event: Event) {
        when (event) {
            // high level actions
            is Event.Load -> {
                customizeFor(Token.usdf, event.amount, event.customizations, event.context)
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

    private fun commitBlend(blendMode: BlendMode, strength: Float?) {
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

    private fun copyConfiguration() {
        val backgroundColors = _state.value.backgroundState.selectedColors.map { it.color }
            .map { it.toHex() }

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "",
                backgroundColors.joinToString()
            )
        )
    }

    override fun cancel() {
        undoStack.clear()
        _state.update { PlaygroundState() }
    }
}