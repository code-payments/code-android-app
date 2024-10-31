package com.getcode.oct24.features.chat.info

import androidx.lifecycle.viewModelScope
import com.getcode.navigation.RoomInfoArgs
import com.getcode.oct24.data.RoomInfo
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.oct24.user.UserManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val chatsController: ChatsController,
    private val resources: ResourceHelper,
    private val userManager: UserManager,
): BaseViewModel2<ChatInfoViewModel.State, ChatInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
){

    data class State(
        val isHost: Boolean = false,
        val roomInfo: RoomInfo = RoomInfo(),
        val requestBeingSent: Boolean = false,
    )

    sealed interface Event {
        data class OnHostStatusChanged(val isHost: Boolean): Event
        data class OnInfoChanged(val args: RoomInfoArgs): Event
        data class OnRequestInFlight(val sending: Boolean): Event
        data object LeaveRoom: Event
        data object OnLeftRoom: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnInfoChanged>()
            .map { it.args.hostId }
            .map { hostId -> userManager.userId == hostId }
            .onEach { isHost ->
                dispatchEvent(Event.OnHostStatusChanged(isHost))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.LeaveRoom -> { state -> state }
                is Event.OnInfoChanged -> { state ->
                    val args = event.args
                    state.copy(
                        roomInfo = RoomInfo(
                            id = args.roomId,
                            number = args.roomNumber,
                            title = args.roomTitle.orEmpty(),
                            memberCount = args.memberCount
                        )
                    )
                }
                Event.OnLeftRoom -> { state -> state }
                is Event.OnRequestInFlight -> { state -> state.copy(requestBeingSent = event.sending) }
                is Event.OnHostStatusChanged -> { state -> state.copy(isHost = event.isHost) }
            }
        }
    }
}