package xyz.flipchat.app.features.chat.name

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
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.PaymentController
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
internal class RoomNameScreenViewModel @Inject constructor(
    userManager: UserManager,
    roomController: RoomController,
    chatsController: ChatsController,
    paymentController: PaymentController,
    profileController: ProfileController,
    resources: ResourceHelper,
) : BaseViewModel2<RoomNameScreenViewModel.State, RoomNameScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val roomId: ID = NoId,
        val previousRoomName: String = "",
        val update: LoadingSuccessState = LoadingSuccessState(),
        val textFieldState: TextFieldState = TextFieldState(""),
    ) {
        val canCheck: Boolean
            get() = textFieldState.text.isNotEmpty()
    }

    sealed interface Event {
        data class OnNewRequest(val id: ID, val title: String) : Event
        data object UpdateName : Event
        data object CreateRoom: Event
        data object PromptForPayment : Event
        data object OnSuccess : Event
        data class OpenRoom(val roomId: ID) : Event
        data object OnError : Event
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

                        dispatchEvent(Event.OnError)
                    }
                    .onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnSuccess)
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CreateRoom>()
            .map { stateFlow.value }
            .onEach {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString().trim()

                chatsController.checkDisplayNameForRoom(text)
                    .onFailure { error ->
                        if (error is CheckDisplayNameError.CantSet) {
                            TopBarManager.showMessage(
                                TopBarManager.TopBarMessage(
                                    title = resources.getString(R.string.error_title_failedToChangeRoomNameBecauseInappropriate),
                                    message = resources.getString(R.string.error_description_failedToChangeRoomNameBecauseInappropriate)
                                )
                            )
                        } else {
                            TopBarManager.showMessage(
                                TopBarManager.TopBarMessage(
                                    title = resources.getString(R.string.error_title_failedToCreateRoom),
                                    message = resources.getString(R.string.error_description_failedToCreateRoom)
                                )
                            )
                        }
                        dispatchEvent(Event.OnError)
                    }
                    .onSuccess {
                        dispatchEvent(Event.PromptForPayment)
                    }
            }.launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.PromptForPayment>()
            .map { profileController.getUserFlags() }
            .mapNotNull {
                it.exceptionOrNull()?.let { error ->
                    error.printStackTrace()
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToCreateRoom),
                            message = resources.getString(R.string.error_description_failedToCreateRoom)
                        )
                    )
                    dispatchEvent(Event.OnError)
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
                            title = stateFlow.value.textFieldState.text.toString().trim(),
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
                                dispatchEvent(Event.OnError)
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

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.CreateRoom -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = true,
                            success = false
                        )
                    )
                }

                is Event.UpdateName -> { state ->
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

                is Event.OpenRoom -> { state -> state }
                is Event.PromptForPayment -> { state ->
                    state.copy(
                        update = LoadingSuccessState(
                            loading = false,
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