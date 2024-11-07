package com.getcode.oct24.features.chat.lookup.confirm

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.navigation.RoomInfoArgs
import com.getcode.oct24.R
import com.getcode.oct24.data.RoomInfo
import com.getcode.oct24.features.login.register.onResult
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class JoinConfirmationViewModel @Inject constructor(
    private val chatsController: ChatsController,
    private val resources: ResourceHelper,
) : BaseViewModel2<JoinConfirmationViewModel.State, JoinConfirmationViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val roomInfo: RoomInfo = RoomInfo(),
        val joining: Boolean = false,
    )

    sealed interface Event {
        data class OnJoinArgsChanged(val args: RoomInfoArgs): Event
        data class OnJoiningChanged(val joining: Boolean): Event
        data object JoinRoom: Event
        data class OnJoinedSuccessfully(val roomId: ID): Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.JoinRoom>()
            .mapNotNull { stateFlow.value.roomInfo.id }
            .onEach {
                dispatchEvent(Event.OnJoiningChanged(true))
            }
            .map {
                chatsController.joinRoom(it)
            }.onResult(
                onError = {
                    dispatchEvent(Event.OnJoiningChanged(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToJoinRoom),
                            resources.getString(R.string.error_description_failedToJoinRoom,
                                stateFlow.value.roomInfo.title
                            )
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnJoiningChanged(false))
                    dispatchEvent(Event.OnJoinedSuccessfully(it.room.id))
                }
            ).launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnJoinArgsChanged -> { state ->
                    val args = event.args
                    state.copy(
                        roomInfo = RoomInfo(
                            id = args.roomId,
                            title = args.roomTitle.orEmpty(),
                            number = args.roomNumber,
                            memberCount = args.memberCount,
                            hostName = args.hostName,
                        ),
                    )
                }

                Event.JoinRoom,
                is Event.OnJoinedSuccessfully -> { state -> state }
                is Event.OnJoiningChanged -> { state -> state.copy(joining = event.joining) }
            }
        }
    }
}