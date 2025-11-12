package com.flipcash.app.bill.customization.internal

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.stack.Stack
import cafe.adriel.voyager.core.stack.mutableStateStackOf
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.ColorStore
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundMode
import com.flipcash.app.bill.customization.State
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.utils.nonce
import com.getcode.ui.utils.hexToColor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

class InternalBillPlaygroundController(
    private val exchange: Exchange,
) : BillPlaygroundController {

    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    override val state: StateFlow<State>
        get() = _state.asStateFlow()

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    private val undoStack = ArrayDeque<State>()

    override val canUndo: Boolean
        get() = !undoStack.isEmpty()

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
            token = token,
            amount = demoAmount,
            disableGestures = true,
            data = payloadInfo.codeData.toList()
        )

        _state.update { it.copy(bill = bill) }
    }

    override fun dispatchEvent(event: Event) {
        when (event) {
            Event.AddSlot -> addSlot()
            is Event.CommitColorChange -> commitColorChangeForSlot(event.color)
            is Event.PreviewColorChange -> previewColorChangeForSlot(event.color)
            Event.CloseHueControls -> closeHueControls()
            Event.OpenHueControls -> openHueControls()
            Event.RemoveSlot -> removeSlot()
            is Event.SelectSlot -> selectSlot(event.slot)
            is Event.LoadBackground -> loadBackground(event.background)
            Event.Undo -> undoLastChange()
        }
    }

    private fun addSlot() {
        if (_state.value.selectedColors.count() < _state.value.maxSlots) {
            _state.updateWithHistory { s ->
                val lastSlotColor = s.selectedColors[s.selectedSlot]
                val insertIndex = s.selectedSlot + 1
                val colors = s.selectedColors.toMutableList().apply {
                    add(insertIndex, lastSlotColor)
                }.toList()

                s.copy(
                    selectedColors = colors,
                    selectedSlot = insertIndex,
                )
            }
        }
    }

    private fun removeSlot() {
        if (_state.value.selectedColors.count() > 1) {
            _state.updateWithHistory { s ->
                val indexToRemove = s.selectedSlot
                val newColors = s.selectedColors.toMutableList().apply { removeAt(indexToRemove) }
                s.copy(
                    selectedColors = newColors,
                    selectedSlot = s.selectedSlot.coerceAtMost(newColors.size - 1),
                )
            }
        }
    }

    private fun selectSlot(slot: Int) {
        _state.update { s ->
            s.copy(selectedSlot = slot)
        }
    }

    private fun commitColorChangeForSlot(color: Color) {
        _state.updateWithHistory { s ->
            val slotIndex = s.selectedSlot
            val updatedColors = s.selectedColors.toMutableList().apply {
                set(slotIndex, ColorStore(color))
            }.toList()
            s.copy(
                selectedColors = updatedColors,
            )
        }
    }

    private fun previewColorChangeForSlot(color: Color) {
        // No history push—treat as transient
        _state.update { s ->
            val slotIndex = s.selectedSlot
            val slot = s.selectedColors[slotIndex]
            val updatedColors = s.selectedColors.toMutableList().apply {
                set(slotIndex, slot.copy(transition = color))
            }.toList()
            s.copy(selectedColors = updatedColors)
        }
    }

    private fun openHueControls() {
        _state.update { s ->
            s.copy(mode = PlaygroundMode.ColorPanel)
        }
    }

    private fun closeHueControls() {
        _state.update { s ->
            s.copy(mode = PlaygroundMode.Presets)
        }
    }

    private fun loadBackground(background: BillBackground) {
        when (val bg = background) {
            is BillBackground.Gradient -> {
                val colors = bg.colors.map { hexToColor(it) }
                _state.updateWithHistory { s ->
                    s.copy(
                        selectedColors = colors.map { ColorStore(it) },
                        selectedSlot = colors.lastIndex
                    )
                }
            }

            is BillBackground.Solid -> {
                _state.updateWithHistory { s ->
                    s.copy(
                        selectedColors = listOf(ColorStore(bg.colorHex)),
                        selectedSlot = 0
                    )
                }
            }
        }
    }

    private fun undoLastChange() {
        if (!undoStack.isEmpty()) {
            val previous = undoStack.removeLast()
            _state.update { previous }
        }
    }

    override fun cancel() {
        undoStack.clear()
        _state.update { State() }
    }

    private fun MutableStateFlow<State>.updateWithHistory(function: (State) -> State) {
        val currentClean = _state.value.copy(
            selectedColors = _state.value.selectedColors.map {
                it.copy(transition = null)
            },
        )
        undoStack.add(currentClean)  // Push clean current
        update { function(currentClean) }  // Apply to clean base
    }
}