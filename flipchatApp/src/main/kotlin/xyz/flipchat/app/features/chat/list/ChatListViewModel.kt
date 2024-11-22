package xyz.flipchat.app.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.services.model.ExtendedMetadata
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.StartGroupChatPaymentMetadata
import xyz.flipchat.services.data.erased
import xyz.flipchat.services.data.typeUrl
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    userManager: xyz.flipchat.services.user.UserManager,
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
        data class OpenRoom(val roomId: ID) : Event
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
            .filterIsInstance<Event.CreateRoomSelected>()
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
                        KinAmount.newInstance(
                            kin = flags.createCost.quarks.toInt(),
                            rate = Rate.oneToOne
                        )

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
                                dispatchEvent(
                                    Event.ShowFullScreenSpinner(
                                        showScrim = false,
                                        showSpinner = false
                                    )
                                )
                                TopBarManager.showMessage(
                                    TopBarManager.TopBarMessage(
                                        resources.getString(R.string.error_title_failedToCreateRoom),
                                        resources.getString(R.string.error_description_failedToCreateRoom)
                                    )
                                )
                            }
                        }.onSuccess {
                                event.acknowledge(true) {
                                    dispatchEvent(
                                        Event.ShowFullScreenSpinner(
                                            showScrim = false,
                                            showSpinner = false
                                        )
                                    )
                                    dispatchEvent(Event.OpenRoom(it.id))
                                    paymentController.cancelPayment(fromUser = false)
                                }
                        }
                    }
                }
            }.launchIn(viewModelScope)
    }

    val chats: Flow<PagingData<ConversationWithMembersAndLastMessage>>
    = chatsController.chats.flow.cachedIn(viewModelScope)

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOpen -> { state -> state }
                is Event.OnNetworkChanged -> { state ->
                    state.copy(networkConnected = event.connected)
                }

                is Event.CreateRoomSelected -> { state -> state }

                is Event.OpenRoom -> { state -> state }
                is Event.ShowFullScreenSpinner -> { state ->
                    state.copy(
                        showFullscreenSpinner = event.showSpinner,
                        showScrim = event.showScrim
                    )
                }

                is Event.OnSelfIdChanged -> { state -> state.copy(selfId = event.id) }
            }
        }
    }
}