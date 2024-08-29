@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.create.byusername

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.clearText
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.network.TipController
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.main.chat.conversation.ConversationViewModel.Event
import com.getcode.view.main.chat.conversation.ConversationViewModel.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ChatByUsernameViewModel @Inject constructor(
    resources: ResourceHelper,
    tipController: TipController,
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
        data class OnSuccess(val username: String) : Event
        data object OnError : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.CheckUsername>()
            .map { stateFlow.value }
            .map {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString()

                runCatching { tipController.fetch(text) }
            }
            .map { it.getOrNull() }
            .onEach { twitterUser ->
                if (twitterUser == null) {
                    dispatchEvent(Event.OnError)
                } else {
                    dispatchEvent(Event.OnSuccess(twitterUser.username))
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