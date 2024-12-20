package xyz.flipchat.app.features.chat.name

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.NoId
import com.getcode.services.utils.onSuccessWithDelay
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.R
import xyz.flipchat.app.features.chat.lookup.confirm.LoadingSuccessState
import xyz.flipchat.chat.RoomController
import xyz.flipchat.services.internal.network.service.SetRoomDisplayNameError
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class RoomNameScreenViewModel @Inject constructor(
    roomController: RoomController,
    resources: ResourceHelper,
) : BaseViewModel2<RoomNameScreenViewModel.State, RoomNameScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val roomId: ID = NoId,
        val previousRoomName: String = "",
        val update: LoadingSuccessState = LoadingSuccessState(),
        val textFieldState: TextFieldState = TextFieldState("   "),
    ) {
        val canCheck: Boolean
            get() = textFieldState.text.isNotEmpty()
    }

    sealed interface Event {
        data class OnNewRequest(val id: ID, val title: String) : Event
        data object UpdateName : Event
        data object OnSuccess : Event
        data class OnError(val reason: String) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.UpdateName>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString().trim()

                roomController.setDisplayName(it.roomId, text)
                    .onFailure { error ->
                        if (error is SetRoomDisplayNameError.CantSet) {
                            TopBarManager.showMessage(
                                TopBarManager.TopBarMessage(
                                    title = resources.getString(R.string.error_title_failedToChangeRoomNameBecauseInappropriate),
                                    message = resources.getString(R.string.error_description_failedToChangeRoomNameBecauseInappropriate)
                                )
                            )
                        } else {
                            TopBarManager.showMessage(
                                TopBarManager.TopBarMessage(
                                    title = resources.getString(R.string.error_title_failedToChangeRoomNameOtherReason),
                                    message = resources.getString(R.string.error_description_failedToChangeRoomNameOtherReason)
                                )
                            )
                        }

                        dispatchEvent(Event.OnError(""))
                    }
                    .onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnSuccess)
                    }
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnError -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = false,
                            success = false
                        )
                    )
                }

                Event.OnSuccess -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = false,
                            success = true
                        )
                    )
                }

                Event.UpdateName -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = true,
                            success = false
                        )
                    )
                }

                is Event.OnNewRequest -> { state ->
                    if (state.roomId != event.id) {
                        state.copy(
                            update = LoadingSuccessState(),
                            roomId = event.id,
                            textFieldState = TextFieldState(initialText = event.title),
                            previousRoomName = event.title,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }
}