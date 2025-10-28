package com.flipcash.app.bill.customization.internal

import androidx.compose.ui.graphics.Color
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.State
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.utils.nonce
import com.getcode.ui.utils.hexToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InternalBillPlaygroundController(
    private val exchange: Exchange,
): BillPlaygroundController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    override val state: StateFlow<State>
        get() = _state.asStateFlow()

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    override fun customizeFor(token: Token) {
        // create amount for the bill
        val demoAmount = LocalFiat.valueExchangeIn(
            amount = 5.toFiat(),
            token = token,
            rate = exchange.rateForUsd()
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
            is Event.ChangeColor -> changeColorForSlot(event.color)
            Event.CloseHueControls -> closeHueControls()
            Event.OpenHueControls -> openHueControls()
            Event.RemoveSlot -> removeSlot()
            is Event.SelectSlot -> selectSlot(event.slot)
            is Event.LoadBackground -> {
                when (val bg = event.background) {
                    is BillBackground.Gradient -> {
                        val colors = bg.colors.map { hexToColor(it) }
                        _state.update {
                            it.copy(
                                selectedColors = colors,
                                selectedSlot = colors.lastIndex,
                            )
                        }
                    }
                    is BillBackground.Solid -> {
                        _state.update {
                            it.copy(
                                selectedColors = listOf(hexToColor(bg.colorHex)),
                                selectedSlot = 0
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addSlot() {
        if (_state.value.selectedColors.count() < _state.value.maxSlots) {
            _state.update {
                val lastSlotColor = it.selectedColors[it.selectedSlot]
                val insertIndex = it.selectedSlot + 1
                val colors = it.selectedColors.toMutableList().apply {
                    add(insertIndex, lastSlotColor)
                }.toList()

                it.copy(selectedColors = colors, selectedSlot = insertIndex)
            }
        }
    }

    private fun removeSlot() {
        if (_state.value.selectedColors.count() > 1) {
            _state.update {
                val indexToRemove = it.selectedSlot
                val newColors = it.selectedColors.toMutableList().apply { removeAt(indexToRemove) }
                it.copy(
                    selectedColors = newColors,
                    selectedSlot = it.selectedSlot.coerceAtMost(newColors.size - 1)
                )
            }
        }
    }

    private fun selectSlot(slot: Int) {
        _state.update { s ->
            s.copy(selectedSlot = slot)
        }
    }

    private fun changeColorForSlot(color: Color) {
        _state.update { s ->
            val slotIndex = s.selectedSlot
            val updatedColors = s.selectedColors.toMutableList().apply {
                set(slotIndex, color)
            }.toList()
            s.copy(
                selectedColors = updatedColors
            )
        }
    }

    private fun openHueControls() {
        _state.update { s ->
            s.copy(hueControlsOpen = true)
        }
    }

    private fun closeHueControls() {
        _state.update { s ->
            s.copy(hueControlsOpen = false)
        }
    }

    override fun cancel() {
        _state.update { State() }
    }
}