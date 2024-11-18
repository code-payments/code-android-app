package xyz.flipchat.app.features.chat.lookup.confirm

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.navigation.RoomInfoArgs
import xyz.flipchat.app.R
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.data.RoomInfo
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import xyz.flipchat.services.data.JoinChatPaymentMetadata
import xyz.flipchat.services.data.erased
import xyz.flipchat.services.data.typeUrl
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class JoinConfirmationViewModel @Inject constructor(
    private val userManager: UserManager,
    private val chatsController: ChatsController,
    private val profileController: ProfileController,
    private val paymentController: PaymentController,
    private val resources: ResourceHelper,
) : BaseViewModel2<JoinConfirmationViewModel.State, JoinConfirmationViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val canJoin: Boolean = false,
        val paymentDestination: PublicKey? = null,
        val paymentReceipt: ID? = null,
        val roomInfo: RoomInfo = RoomInfo(),
        val joining: Boolean = false,
    )

    sealed interface Event {
        data class OnJoinArgsChanged(val args: RoomInfoArgs) : Event
        data class OnJoiningChanged(val joining: Boolean) : Event
        data object JoinRoomClicked : Event
        data object JoinRoom : Event
        data class OnJoinedSuccessfully(val roomId: ID) : Event
        data class OnDestinationChanged(val destination: PublicKey) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnJoinArgsChanged>()
            .mapNotNull { it.args.hostId }
            .map { profileController.getPaymentDestinationForUser(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToJoinRoom),
                            resources.getString(
                                R.string.error_description_failedToJoinRoom,
                                stateFlow.value.roomInfo.title
                            )
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnDestinationChanged(it))
                }
            ).launchIn(viewModelScope)


        eventFlow
            .filterIsInstance<Event.JoinRoomClicked>()
            .map { stateFlow.value.roomInfo }
            .onEach {
                val destination = stateFlow.value.paymentDestination ?: return@onEach
                val amount =
                    KinAmount.newInstance(kin = it.coverCharge.quarks.toInt(), rate = Rate.oneToOne)

                dispatchEvent(Event.OnJoiningChanged(true))
                if (userManager.userId == it.hostId) {
                    // we are the host; just allow join
                    val roomId = stateFlow.value.roomInfo.id.orEmpty()
                    chatsController.joinRoom(roomId, paymentId = null)
                } else {
                    val joinChatMetadata = JoinChatPaymentMetadata(
                        userId = userManager.userId!!,
                        chatId = it.id!!
                    )

                    val metadata = ExtendedMetadata.Any(
                        data = joinChatMetadata.erased(),
                        typeUrl = joinChatMetadata.typeUrl
                    )

                    paymentController.presentPublicPaymentConfirmation(
                        amount = amount,
                        destination = destination,
                        metadata = metadata
                    )
                }
            }.launchIn(viewModelScope)

        paymentController.eventFlow
            .filterIsInstance<PaymentEvent.OnPaymentCancelled>()
            .onEach { dispatchEvent(Event.OnJoiningChanged(false)) }
            .launchIn(viewModelScope)

        paymentController.eventFlow
            .filterIsInstance<PaymentEvent.OnPaymentError>()
            .onEach { dispatchEvent(Event.OnJoiningChanged(false)) }
            .launchIn(viewModelScope)

        paymentController.eventFlow
            .filterIsInstance<PaymentEvent.OnPaymentSuccess>()
            .map {
                val roomId = stateFlow.value.roomInfo.id.orEmpty()
                chatsController.joinRoom(roomId, it.intentId)
            }
            .onResult(
                onError = {
                    dispatchEvent(Event.OnJoiningChanged(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToJoinRoom),
                            resources.getString(
                                R.string.error_description_failedToJoinRoom,
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
                            hostId = args.hostId,
                            hostName = args.hostName,
                            coverCharge = Kin.fromQuarks(args.coverChargeQuarks)
                        ),
                    )
                }

                Event.JoinRoomClicked,
                Event.JoinRoom,
                is Event.OnJoinedSuccessfully -> { state -> state }

                is Event.OnJoiningChanged -> { state -> state.copy(joining = event.joining) }
                is Event.OnDestinationChanged -> { state ->
                    state.copy(
                        paymentDestination = event.destination,
                        canJoin = true
                    )
                }
            }
        }
    }
}