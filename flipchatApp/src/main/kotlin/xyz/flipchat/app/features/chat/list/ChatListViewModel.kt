package xyz.flipchat.app.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.kin
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.onError
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.extensions.titleOrFallback
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    userManager: UserManager,
    private val chatsController: ChatsController,
    networkObserver: NetworkConnectivityListener,
    resources: ResourceHelper,
) : BaseViewModel2<ChatListViewModel.State, ChatListViewModel.Event>(
    initialState = State(userManager.userId),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val selfId: ID? = null,
        val showScrim: Boolean = false,
        val showFullscreenSpinner: Boolean = false,
        val networkConnected: Boolean = true,
        val chatTapCount: Int = 0,
        val isLoggedIn: Boolean = false,
        val isLogOutEnabled: Boolean = false,
        val createRoomCost: Kin = 0.kin,
    )

    sealed interface Event {
        data class OnSelfIdChanged(val id: ID?) : Event
        data class OnLoggedInStateChanged(val loggedIn: Boolean) : Event
        data class ShowFullScreenSpinner(
            val showScrim: Boolean = true,
            val showSpinner: Boolean = true
        ) :
            Event

        data class OnCreateCostChanged(val cost: Kin): Event
        data class OnNetworkChanged(val connected: Boolean) : Event
        data object OnOpen : Event
        data object CreateRoomSelected : Event
        data object CreateRoom : Event
        data object NeedsAccountCreated : Event
        data object OnAccountCreated : Event
        data class OpenRoom(val roomId: ID) : Event
        data object OnChatsTapped : Event
        data class MuteRoom(val roomId: ID) : Event
        data class UnmuteRoom(val roomId: ID) : Event
        data object OnLogOutUnlocked : Event
    }

    init {
        userManager.state
            .map { it.userId }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnSelfIdChanged(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .mapNotNull { it.flags }
            .map { it.createCost }
            .onEach { dispatchEvent(Event.OnCreateCostChanged(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .map { it.authState }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnLoggedInStateChanged(it is AuthState.LoggedIn)) }
            .launchIn(viewModelScope)

        networkObserver.state
            .map { it.connected }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnNetworkChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnChatsTapped>()
            .map { stateFlow.value.chatTapCount }
            .filter { it >= TAP_THRESHOLD }
            .filterNot { stateFlow.value.isLogOutEnabled }
            .onEach { dispatchEvent(Event.OnLogOutUnlocked) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.MuteRoom>()
            .map { it.roomId }
            .map { chatsController.muteRoom(it) }
            .onError {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        resources.getString(R.string.error_title_failedToMuteChat),
                        resources.getString(R.string.error_description_failedToMuteChat)
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnmuteRoom>()
            .map { it.roomId }
            .map { chatsController.unmuteRoom(it) }
            .onError {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        resources.getString(R.string.error_title_failedToUnmuteChat),
                        resources.getString(R.string.error_description_failedToUnmuteChat)
                    )
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CreateRoomSelected>()
            .map { userManager.authState }
            .onEach {
                if (it is AuthState.LoggedIn) {
                    dispatchEvent(Event.CreateRoom)
                } else {
                    dispatchEvent(Event.NeedsAccountCreated)
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccountCreated>()
            .onEach { delay(400) }
            .onEach { dispatchEvent(Event.CreateRoom) }
            .launchIn(viewModelScope)
    }

    val chats: Flow<PagingData<ConversationWithMembersAndLastMessage>> =
        userManager.state
            .map { it.authState }
            .map { it.canOpenChatStream() }
            .distinctUntilChanged()
            .flatMapLatest { canOpen ->
                if (canOpen) {
                    chatsController.chats.flow
                } else {
                    flowOf(PagingData.empty())
                }
            }.map { page ->
                page.map {
                    it.copy(
                        conversation = it.conversation.copy(
                            title = it.conversation.titleOrFallback(
                                resources = resources,
                            )
                        )
                    )
                }
            }
            .cachedIn(viewModelScope)

    companion object {
        private const val TAP_THRESHOLD = 6
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnNetworkChanged -> { state ->
                    state.copy(networkConnected = event.connected)
                }

                is Event.OnCreateCostChanged -> { state ->
                    state.copy(createRoomCost = event.cost)
                }

                is Event.OnChatsTapped -> { state ->
                    if (state.chatTapCount >= TAP_THRESHOLD) state
                    else state.copy(chatTapCount = state.chatTapCount + 1)
                }

                is Event.OnLogOutUnlocked -> { state -> state.copy(isLogOutEnabled = true) }
                is Event.OpenRoom -> { state -> state }
                is Event.ShowFullScreenSpinner -> { state ->
                    state.copy(
                        showFullscreenSpinner = event.showSpinner,
                        showScrim = event.showScrim
                    )
                }

                is Event.OnSelfIdChanged -> { state -> state.copy(selfId = event.id) }

                is Event.OnLoggedInStateChanged -> { state -> state.copy(isLoggedIn = event.loggedIn) }

                is Event.NeedsAccountCreated,
                is Event.OnAccountCreated,
                is Event.OnOpen,
                is Event.CreateRoomSelected,
                is Event.CreateRoom,
                is Event.MuteRoom,
                is Event.UnmuteRoom -> { state -> state }
            }
        }
    }
}