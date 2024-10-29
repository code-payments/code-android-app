package com.flipchat.features.chat.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.oct24.R
import com.getcode.oct24.data.Room
import com.getcode.oct24.domain.model.chat.ConversationWithMembers
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage
import com.getcode.oct24.features.login.register.onResult
import com.getcode.oct24.network.controllers.ChatsController
import com.getcode.oct24.user.UserManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    userManager: UserManager,
    private val chatsController: ChatsController,
    networkObserver: NetworkConnectivityListener,
    resources: ResourceHelper,
): BaseViewModel2<ChatListViewModel.State, ChatListViewModel.Event>(
    initialState = State(userManager.userId),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val selfId: ID? = null,
        val showFullscreenSpinner: Boolean = false,
        val networkConnected: Boolean = true,
    )

    sealed interface Event {
        data class OnSelfIdChanged(val id: ID?): Event
        data class ShowFullScreenSpinner(val show: Boolean): Event
        data class OnNetworkChanged(val connected: Boolean): Event
        data object OnOpen: Event
        data object CreateRoom: Event
        data class OpenRoom(val room: Room): Event
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
            .filterIsInstance<Event.OnOpen>()
             .onEach { chatsController.openEventStream(viewModelScope) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CreateRoom>()
            .onEach { dispatchEvent(Event.ShowFullScreenSpinner(true)) }
            .map { chatsController.createGroup() }
            .onResult(
                onError = {
                    dispatchEvent(Event.ShowFullScreenSpinner(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToCreateRoom),
                            resources.getString(R.string.error_description_failedToCreateRoom)
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.ShowFullScreenSpinner(false))
                    dispatchEvent(Event.OpenRoom(it))
                }
            )
            .launchIn(viewModelScope)
    }

    val chats: Flow<PagingData<ConversationWithMembersAndLastMessage>> get() = chatsController.chats.flow

    override fun onCleared() {
        super.onCleared()
        chatsController.closeEventStream()
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnOpen -> { state -> state }
                is Event.OnNetworkChanged -> { state ->
                    state.copy(networkConnected = event.connected)
                }
                is Event.CreateRoom -> { state -> state }
                is Event.OpenRoom -> { state -> state }
                is Event.ShowFullScreenSpinner -> { state -> state.copy(showFullscreenSpinner = event.show) }
                is Event.OnSelfIdChanged -> { state -> state.copy(selfId = event.id) }
            }
        }
    }
}