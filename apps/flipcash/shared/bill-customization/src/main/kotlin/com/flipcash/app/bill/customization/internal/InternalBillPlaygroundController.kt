package com.flipcash.app.bill.customization.internal

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.PlaygroundFeature
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundState
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
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

@OptIn(ExperimentalStdlibApi::class)
class InternalBillPlaygroundController(
    private val clipboard: ClipboardManager,
) : BillPlaygroundController, ViewModel() {

    private val backgroundController = BackgroundController { pushUndoSnapshot() }

    private val _state: MutableStateFlow<PlaygroundState> = MutableStateFlow(PlaygroundState())
    override val state: StateFlow<PlaygroundState> = _state.asStateFlow()

    init {
        combine(
            _state,
            backgroundController.state
        ) { currentPlayground, currentBackground ->
            currentPlayground.copy(
                backgroundState = currentBackground
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
        )

        undoStack.addLast(cleanSnapshot)
    }

    override val canUndo: Boolean
        get() = !undoStack.isEmpty()

    override val canCopy: Boolean
        get() = true

    override fun customizeFor(token: Token) {
        // create amount for the bill
        val demoAmount = LocalFiat(
            usdc = 5.toFiat(),
        )

        // provide bill "data" to render the scan code
        val payloadInfo = OpenCodePayload(
            kind = PayloadKind.MultiMintCash,
            value = demoAmount.nativeAmount,
            nonce = nonce
        )
        // create bill for token
        val bill = Bill.Cash(
            token = token.copy(billCustomizations = null),
            amount = demoAmount,
            disableGestures = true,
            data = payloadInfo.codeData.toList()
        )

        _state.update { it.copy(bill = bill) }
    }

    override fun dispatchEvent(event: Event) {
        when (event) {
            is Event.SelectFeature -> selectFeature(event.feature)
            Event.AddSlot -> addSlot()
            is Event.CommitColorChange -> commitColorChangeForSlot(event.hsv)
            is Event.PreviewColorChange -> previewColorChangeForSlot(event.hsv)
            Event.CloseHueControls -> closeHueControls()
            Event.OpenHueControls -> openHueControls()
            Event.RemoveSlot -> removeSlot()
            is Event.SelectSlot -> selectSlot(event.slot)
            is Event.LoadBackground -> loadBackground(event.background)
            Event.Undo -> undoLastChange()
            Event.Copy -> copyConfiguration()
        }
    }

    private fun selectFeature(feature: PlaygroundFeature) {
        _state.update {
            it.copy(selectedFeature = feature)
        }
    }

    private fun addSlot() {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.addSlot()
            }
        }
    }

    private fun removeSlot() {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.removeSlot()
            }
        }
    }

    private fun selectSlot(slot: Int) {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.selectSlot(slot)
            }
        }
    }

    private fun commitColorChangeForSlot(hsv: Hsv) {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.commitColorChangeForSlot(hsv)
            }
        }
    }

    private fun previewColorChangeForSlot(hsv: Hsv) {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.previewColorChangeForSlot(hsv)
            }
        }
    }

    private fun openHueControls() {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.openHueControls()
            }
        }
    }

    private fun closeHueControls() {
        when (_state.value.selectedFeature) {
            PlaygroundFeature.Background -> {
                backgroundController.closeHueControls()
            }
        }
    }

    private fun loadBackground(background: BillBackground) {
        backgroundController.load(background)
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
        // ...

        // Finally emit the merged state
        _state.update {
            it.copy(
                backgroundState = backgroundController.getCurrentCleanState(),
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