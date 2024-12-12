package xyz.flipchat.app.features.login.register

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.controllers.CodeController
import xyz.flipchat.services.user.AuthState
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class RegisterDisplayNameViewModel @Inject constructor(
    authManager: AuthManager,
    codeController: CodeController,
    userManager: UserManager,
): BaseViewModel2<RegisterDisplayNameViewModel.State, RegisterDisplayNameViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val checkingDisplayName: Boolean = false,
        val isValidDisplayName: Boolean = false,
        val textFieldState: TextFieldState = TextFieldState(),
    ) {
        val canAdvance: Boolean
            get() = textFieldState.text.isNotEmpty()
    }

    sealed interface Event {
        data object RegisterDisplayName : Event
        data object OnSuccess : Event
        data class OnError(val reason: String) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.RegisterDisplayName>()
            .map { stateFlow.value }
            .map {
                val textFieldState = it.textFieldState
                val text = textFieldState.text.toString().trim()

                if (userManager.authState == AuthState.Unregistered) {
                    userManager.set(displayName = text)
                    Result.success(userManager.userId!!)
                } else {
                    authManager.register(text)
                }
            }
            .onResult(
                onError = { dispatchEvent(Event.OnError(it.message ?: "Something went wrong")) },
                onSuccess = { dispatchEvent(Event.OnSuccess) }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSuccess>()
            .onEach {
                codeController.fetchBalance()
                    .onFailure { it.printStackTrace() }
                    .onSuccess { codeController.requestAirdrop() }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnError>()
            .onEach {
                TopBarManager.showMessage(
                    TopBarManager.TopBarMessage(
                        title = "Create Account Failed",
                        message = it.reason
                    )
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.RegisterDisplayName -> { state ->
                    state.copy(checkingDisplayName = true)
                }
                is Event.OnError -> { state ->
                    state.copy(checkingDisplayName = false)
                }
                Event.OnSuccess -> { state ->
                    state.copy(isValidDisplayName = true)
                }
            }
        }
    }
}

fun <T> Flow<Result<T>>.onResult(onError: (Throwable) -> Unit = { }, onSuccess: (T) -> Unit = { }): Flow<Result<T>> {
    return this.map {
        it.onSuccess(onSuccess).onFailure(onError)
    }
}

fun <T> Flow<Result<T>>.onError(block: (Throwable) -> Unit): Flow<Result<T>> {
    return this.map {
        it.onFailure(block)
    }
}