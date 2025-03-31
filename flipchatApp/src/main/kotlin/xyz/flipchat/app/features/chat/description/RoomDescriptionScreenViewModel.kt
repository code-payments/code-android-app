package xyz.flipchat.app.features.chat.description

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.NoId
import com.getcode.services.model.ExtendedMetadata
import com.getcode.services.utils.onSuccessWithDelay
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import xyz.flipchat.app.R
import xyz.flipchat.chat.RoomController
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.metadata.StartGroupChatPaymentMetadata
import xyz.flipchat.services.data.metadata.erased
import xyz.flipchat.services.data.metadata.typeUrl
import xyz.flipchat.services.internal.network.service.CheckDisplayNameError
import xyz.flipchat.services.internal.network.service.SetRoomDisplayNameError
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class RoomDescriptionScreenViewModel @Inject constructor(
    roomController: RoomController,
    resources: ResourceHelper,
) : BaseViewModel2<RoomDescriptionScreenViewModel.State, RoomDescriptionScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val roomId: ID = NoId,
        val previousRoomDescription: String = "",
        val maxLimit: Int = 160,
        val update: LoadingSuccessState = LoadingSuccessState(),
        val textFieldState: TextFieldState = TextFieldState(""),
    ) {
        val isApproachingLengthOrOver: Boolean
            get() =  textFieldState.text.length >= 150

        val isLimitValid: Boolean
            get() = textFieldState.text.length <= 160

        val canCheck: Boolean
            get() = textFieldState.text.isNotEmpty() && isLimitValid
    }

    sealed interface Event {
        data class OnNewRequest(val id: ID, val description: String) : Event
        data object UpdateDescription : Event
        data object OnError : Event
        data object OnSuccess : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.UpdateDescription>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString().trim()

                roomController.setDescription(it.roomId, text)
                    .onFailure {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                title = resources.getString(R.string.error_title_failedToChangeRoomDescription),
                                message = resources.getString(R.string.error_description_failedToChangeRoomDescription)
                            )
                        )

                        dispatchEvent(Event.OnError)
                    }
                    .onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnSuccess)
                    }
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateDescription -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = true,
                            success = false
                        )
                    )
                }

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

                is Event.OnNewRequest -> { state ->
                    if (state.roomId != event.id) {
                        state.copy(
                            update = LoadingSuccessState(),
                            roomId = event.id,
                            textFieldState = TextFieldState(initialText = event.description),
                            previousRoomDescription = event.description,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }
}