package com.getcode.oct24.features.chat.lookup

import androidx.lifecycle.viewModelScope
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class LookupRoomViewModel @Inject constructor(

): BaseViewModel2<LookupRoomViewModel.State, LookupRoomViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(
            amountData = NumberInputHelper.AmountAnimatedData("")
        ),
        val canLookup: Boolean = false,
    )

    sealed interface Event {
        data class OnNumberPressed(val number: Int): Event
        data object OnBackspace: Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false): Event
        data class OnRoomNumberChanged(val animatedInputUiModel: AmountAnimatedInputUiModel): Event
    }

    init {
        numberInputHelper.reset()

        eventFlow
            .filterIsInstance<Event.OnNumberPressed>()
            .map { it.number }
            .onEach { number ->
                numberInputHelper.maxLength = 9
                numberInputHelper.onNumber(number)
                dispatchEvent(Event.OnEnteredNumberChanged())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBackspace>()
            .onEach {
                numberInputHelper.onBackspace()
                dispatchEvent(Event.OnEnteredNumberChanged(true))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnEnteredNumberChanged>()
            .map { it.backspace }
            .onEach { backspace ->
                val current = stateFlow.value.amountAnimatedModel
                val model = stateFlow.value.amountAnimatedModel
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = false)

                println(amount)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnRoomNumberChanged(updated))
            }.launchIn(viewModelScope)
    }


    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnRoomNumberChanged -> { state ->
                    val room = event.animatedInputUiModel.amountData.amount.toIntOrNull()
                    state.copy(
                        amountAnimatedModel = event.animatedInputUiModel,
                        canLookup = (room ?: 0) > 0
                    )
                }

                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed -> { state -> state }
            }
        }
    }
}