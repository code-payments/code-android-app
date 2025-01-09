package xyz.flipchat.app.features.chat.info

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.navigation.RoomInfoArgs
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.onResult
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.chat.RoomController
import xyz.flipchat.app.data.RoomInfo
import xyz.flipchat.app.util.IntentUtils
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val roomController: RoomController,
    private val resources: ResourceHelper,
    private val userManager: UserManager,
    labs: Labs,
) : BaseViewModel2<ChatInfoViewModel.State, ChatInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isHost: Boolean = false,
        val roomNameChangesEnabled: Boolean = false,
        val roomInfo: RoomInfo = RoomInfo(),
        val requestBeingSent: Boolean = false,
    )

    sealed interface Event {
        data class OnRoomNameChangesEnabled(val enabled: Boolean) : Event
        data class OnHostStatusChanged(val isHost: Boolean) : Event
        data class OnInfoChanged(val args: RoomInfoArgs) : Event
        data class OnRequestInFlight(val sending: Boolean) : Event
        data class OnMembersUpdated(val count: Int) : Event
        data class OnChangeCover(val roomId: ID) : Event
        data class OnCoverChanged(val cover: Kin) : Event
        data class OnChangeName(val id: ID, val title: String) : Event
        data class OnNameChanged(val name: String) : Event
        data object OnShareRoomClicked : Event
        data class ShareRoom(val intent: Intent) : Event
        data object LeaveRoom : Event
        data object OnLeaveRoomConfirmed : Event
        data object OnLeftRoom : Event
    }

    init {
        labs.observe(Lab.RoomNameChanges)
            .onEach { dispatchEvent(Event.OnRoomNameChangesEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnInfoChanged>()
            .map { it.args.ownerId }
            .map { hostId -> userManager.userId == hostId }
            .onEach { isHost ->
                dispatchEvent(Event.OnHostStatusChanged(isHost))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnInfoChanged>()
            .mapNotNull { it.args.roomId }
            .flatMapLatest { roomController.observeConversation(it) }
            .mapNotNull { it }
            .map { Triple(it.conversation.title, it.members.count(), it.conversation.coverCharge) }
            .onEach { (name, members, cover) ->
                dispatchEvent(Event.OnNameChanged(name))
                dispatchEvent(Event.OnMembersUpdated(members))
                dispatchEvent(Event.OnCoverChanged(cover))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LeaveRoom>()
            .map { stateFlow.value.roomInfo.number }
            .onEach { roomNumber ->
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.title_leaveRoom),
                        subtitle = resources.getString(R.string.subtitle_leaveRoom),
                        positiveText = resources.getString(
                            R.string.action_leaveRoomByName,
                            resources.getString(R.string.title_implicitRoomTitle, roomNumber)
                        ),
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

        eventFlow
            .filterIsInstance<Event.OnShareRoomClicked>()
            .map { IntentUtils.shareRoom(stateFlow.value.roomInfo.roomNumber) }
            .onEach { dispatchEvent(Event.ShareRoom(it)) }
            .launchIn(viewModelScope)
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
                            hostId = args.ownerId,
                            hostName = args.hostName,
                            roomNumber = args.roomNumber,
                            coverCharge = Kin.fromQuarks(args.coverChargeQuarks)
                        )
                    )
                }

                is Event.OnChangeCover,
                Event.OnLeaveRoomConfirmed,
                is Event.OnChangeName,
                is Event.OnShareRoomClicked,
                is Event.ShareRoom,
                Event.OnLeftRoom -> { state -> state }

                is Event.OnRequestInFlight -> { state -> state.copy(requestBeingSent = event.sending) }
                is Event.OnHostStatusChanged -> { state -> state.copy(isHost = event.isHost) }
                is Event.OnCoverChanged -> { state ->
                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            coverCharge = event.cover,
                        )
                    )
                }

                is Event.OnNameChanged -> { state ->
                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            title = event.name,
                        )
                    )
                }

                is Event.OnMembersUpdated -> { state ->
                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            memberCount = event.count,
                        )
                    )
                }

                is Event.OnRoomNameChangesEnabled -> { state -> state.copy(roomNameChangesEnabled = event.enabled) }
            }
        }
    }
}