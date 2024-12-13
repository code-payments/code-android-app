package xyz.flipchat.app.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.services.model.ExtendedMetadata
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import xyz.flipchat.app.R
import xyz.flipchat.app.features.chat.conversation.ConversationViewModel.Event
import xyz.flipchat.app.features.login.register.onError
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.StartGroupChatPaymentMetadata
import xyz.flipchat.services.data.erased
import xyz.flipchat.services.data.typeUrl
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    userManager: UserManager,
    private val chatsController: ChatsController,
    private val paymentController: PaymentController,
    private val profileController: ProfileController,
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
        val isLogOutEnabled: Boolean = false,
    )

    sealed interface Event {
        data class OnSelfIdChanged(val id: ID?) : Event
        data class ShowFullScreenSpinner(
            val showScrim: Boolean = true,
            val showSpinner: Boolean = true
        ) :
            Event

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
            .onEach {
                dispatchEvent(Event.OnSelfIdChanged(it))
            }.launchIn(viewModelScope)

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

        eventFlow.filterIsInstance<Event.CreateRoom>()
            .map { profileController.getUserFlags() }
            .mapNotNull {
                it.exceptionOrNull()?.let {
                    return@mapNotNull null
                }

                it.getOrNull()?.let { flags ->
                    val startGroupMetadata = StartGroupChatPaymentMetadata(
                        userId = userManager.userId!!
                    )

                    val metadata = ExtendedMetadata.Any(
                        data = startGroupMetadata.erased(),
                        typeUrl = startGroupMetadata.typeUrl
                    )

                    val amount =
                        KinAmount.fromQuarks(flags.createCost.quarks)

                    paymentController.presentPublicPaymentConfirmation(
                        amount = amount,
                        destination = flags.feeDestination,
                        metadata = metadata
                    )
                }
            }.flatMapLatest {
                paymentController.eventFlow.take(1)
            }.onEach { event ->
                when (event) {
                    PaymentEvent.OnPaymentCancelled -> Unit
                    is PaymentEvent.OnPaymentError -> Unit

                    is PaymentEvent.OnPaymentSuccess -> {
                        chatsController.createGroup(
                            title = null,
                            participants = emptyList(),
                            paymentId = event.intentId
                        ).onFailure {
                            event.acknowledge(false) {
                                TopBarManager.showMessage(
                                    TopBarManager.TopBarMessage(
                                        resources.getString(R.string.error_title_failedToCreateRoom),
                                        resources.getString(R.string.error_description_failedToCreateRoom)
                                    )
                                )
                            }
                        }.onSuccess {
                            event.acknowledge(true) {
                                dispatchEvent(Event.OpenRoom(it.room.id))
                            }
                        }
                    }
                }
            }.launchIn(viewModelScope)
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
            }
            .cachedIn(viewModelScope)

    companion object {
        private const val TAP_THRESHOLD = 6
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnNetworkChanged -> { state ->
                    state.copy(networkConnected = event.connected)
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