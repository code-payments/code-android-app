package xyz.flipchat.app.features.chat.cover

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.chat.RoomController
import javax.inject.Inject

@HiltViewModel
class CoverChargeViewModel @Inject constructor(
    roomController: RoomController,
    resources: ResourceHelper,
) : BaseViewModel2<CoverChargeViewModel.State, CoverChargeViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    private val numberInputHelper = NumberInputHelper()

    data class State(
        val roomId: ID? = null,
        val submitting: Boolean = false,
        val success: Boolean = false,
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(
            amountData = NumberInputHelper.AmountAnimatedData()
        ),
        val canChange: Boolean = false,
    )

    sealed interface Event {
        data class OnRoomIdChanged(val roomId: ID) : Event
        data class OnNumberPressed(val number: Int) : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnCoverChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) : Event
        data object OnChangeCover : Event
        data class OnChangingCover(val changing: Boolean): Event
        data object OnCoverChangedSuccessfully : Event
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
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnCoverChanged(updated))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChangeCover>()
            .onEach { dispatchEvent(Event.OnChangingCover(true)) }
            .mapNotNull {
                stateFlow.value.roomId ?: return@mapNotNull null
                stateFlow.value.roomId!! to stateFlow.value.amountAnimatedModel.amountData.amount.toLong()
            }.map { (roomId, value) ->
                val kin = Kin.fromKin(value).toKin().toInt()
                roomController.setCoverCharge(roomId, KinAmount.newInstance(kin, Rate.oneToOne))
            }.onResult(
                onError = {
                    dispatchEvent(Event.OnChangingCover(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToChangeCover),
                            resources.getString(R.string.error_description_failedToChangeCover)
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnChangingCover(false))
                    dispatchEvent(Event.OnCoverChangedSuccessfully)
                }
            ).launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChangingCover -> { state -> state.copy(submitting = event.changing) }
                Event.OnCoverChangedSuccessfully -> { state -> state.copy(success = true) }
                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnChangeCover,
                is Event.OnNumberPressed -> { state -> state }

                is Event.OnCoverChanged -> { state ->
                    val cover = event.amountAnimatedModel.amountData.amount.toIntOrNull()
                    state.copy(
                        amountAnimatedModel = event.amountAnimatedModel,
                        canChange = (cover ?: 0) > 0
                    )
                }

                is Event.OnRoomIdChanged -> { state -> state.copy(roomId = event.roomId) }
            }
        }
    }
}