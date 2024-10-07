@file:OptIn(ExperimentalFoundationApi::class)

package com.flipchat.features.chat.lookup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.getcode.oct24.R
import com.getcode.manager.TopBarManager
import com.getcode.model.TwitterUser
import com.getcode.network.TwitterUserController
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatByUsernameViewModel @Inject constructor(
    resources: ResourceHelper,
    twitterUserController: TwitterUserController,
): BaseViewModel2<ChatByUsernameViewModel.State, ChatByUsernameViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val checkingUsername: Boolean = false,
        val isValidUsername: Boolean = false,
        val textFieldState: TextFieldState = TextFieldState(),
    ) {
        val canAdvance: Boolean
            get() = textFieldState.text.isNotEmpty()
    }

    sealed interface Event {
        data object CheckUsername : Event
        data class OnSuccess(val user: TwitterUser) : Event
        data object OnError : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.CheckUsername>()
            .map { stateFlow.value }
            .map {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString()

                runCatching { twitterUserController.fetchUser(text) }
            }
            .map { it.getOrNull() }
            .onEach { twitterUser ->
                if (twitterUser == null) {
                    dispatchEvent(Event.OnError)
                } else {
                    dispatchEvent(Event.OnSuccess(twitterUser))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnError>()
            .onEach {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        title = resources.getString(R.string.error_title_usernameNotFound),
                        message = resources.getString(R.string.error_description_usernameNotFound)
                    )
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.CheckUsername -> { state ->
                    state.copy(checkingUsername = true)
                }
                Event.OnError -> { state ->
                    state.copy(checkingUsername = false)
                }
                is Event.OnSuccess -> { state ->
                    state.copy(checkingUsername = false, isValidUsername = true)
                }
            }
        }
    }
}