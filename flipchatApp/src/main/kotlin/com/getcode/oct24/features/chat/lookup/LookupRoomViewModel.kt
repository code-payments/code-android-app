package com.getcode.oct24.features.chat.lookup

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.oct24.R
import com.getcode.oct24.data.RoomWithMembers
import com.getcode.oct24.features.login.register.onResult
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class LookupRoomViewModel @Inject constructor(
    chatsController: ChatsController,
    resources: ResourceHelper,
): BaseViewModel2<LookupRoomViewModel.State, LookupRoomViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val lookingUp: Boolean = false,
        val success: Boolean = false,
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(
            amountData = NumberInputHelper.AmountAnimatedData("")
        ),
        val canLookup: Boolean = false,
    )

    sealed interface Event {
        data class OnLookingUpRoom(val requesting: Boolean): Event
        data class OnNumberPressed(val number: Int): Event
        data object OnBackspace: Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false): Event
        data class OnRoomNumberChanged(val animatedInputUiModel: AmountAnimatedInputUiModel): Event
        data object OnLookupRoom: Event
        data object OnRoomFound: Event
        data class OnOpenConfirmation(val room: RoomWithMembers): Event
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

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnRoomNumberChanged(updated))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLookupRoom>()
            .onEach { dispatchEvent(Event.OnLookingUpRoom(true)) }
            .map { stateFlow.value.amountAnimatedModel.amountData.amount.toLong() }
            .map { chatsController.lookupRoom(it) }
            .onResult(
                onError = {
                    dispatchEvent(Event.OnLookingUpRoom(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToJoinRoom),
                            resources.getString(R.string.error_description_failedToJoinRoom, stateFlow.value.amountAnimatedModel.amountData.amount)
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnLookingUpRoom(true))
                    dispatchEvent(Event.OnRoomFound)
                    dispatchEvent(Event.OnOpenConfirmation(it))
                }
            ).launchIn(viewModelScope)
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
                Event.OnLookupRoom,
                is Event.OnOpenConfirmation,
                is Event.OnNumberPressed -> { state -> state }

                is Event.OnRoomFound -> { state -> state.copy(success = true) }
                is Event.OnLookingUpRoom -> { state -> state.copy(lookingUp = event.requesting) }
            }
        }
    }
}