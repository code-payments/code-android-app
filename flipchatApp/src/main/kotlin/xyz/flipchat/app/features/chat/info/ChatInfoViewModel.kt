package xyz.flipchat.app.features.chat.info

import androidx.lifecycle.viewModelScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.Kin
import com.getcode.navigation.RoomInfoArgs
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.onResult
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.chat.RoomController
import xyz.flipchat.app.data.RoomInfo
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val roomController: RoomController,
    private val resources: ResourceHelper,
    private val userManager: xyz.flipchat.services.user.UserManager,
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
        data object OnLeaveRoomConfirmed: Event
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

        eventFlow
            .filterIsInstance<Event.LeaveRoom>()
            .map { stateFlow.value.roomInfo.title }
            .onEach { roomTitle ->
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.title_leaveRoom),
                        subtitle = resources.getString(R.string.subtitle_leaveRoom),
                        positiveText = resources.getString(R.string.action_leaveRoomByName, roomTitle),
                        negativeText = "",
                        tertiaryText = resources.getString(R.string.action_cancel),
                        onPositive = { dispatchEvent(Event.OnLeaveRoomConfirmed) },
                        onNegative = { },
                        type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                        showScrim = true,
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLeaveRoomConfirmed>()
            .map { stateFlow.value.roomInfo.id }
            .mapNotNull {
                if (it == null) {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToLeaveRoom),
                            message = resources.getString(R.string.error_description_failedToLeaveRoom)
                        )
                    )
                    return@mapNotNull null
                }
                dispatchEvent(Event.OnRequestInFlight(true))
                roomController.leaveRoom(it)
            }.onResult(
                onError = {
                    dispatchEvent(Event.OnRequestInFlight(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToLeaveRoom),
                            message = resources.getString(R.string.error_description_failedToLeaveRoom)
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnRequestInFlight(false))
                    dispatchEvent(Event.OnLeftRoom)
                }
            ).launchIn(viewModelScope)
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
                            memberCount = args.memberCount,
                            hostId = args.hostId,
                            hostName = args.hostName,
                            coverCharge = Kin.fromQuarks(args.coverChargeQuarks)
                        )
                    )
                }
                Event.OnLeaveRoomConfirmed -> { state -> state }
                Event.OnLeftRoom -> { state -> state }
                is Event.OnRequestInFlight -> { state -> state.copy(requestBeingSent = event.sending) }
                is Event.OnHostStatusChanged -> { state -> state.copy(isHost = event.isHost) }
            }
        }
    }
}