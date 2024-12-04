package xyz.flipchat.app.features.login

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.features.login.register.onResult
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
) : BaseViewModel2<LoginViewModel.State, LoginViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val creatingAccount: Boolean = false,
    )

    sealed interface Event {
        data object CreateAccount: Event
        data object OnAccountCreated: Event
        data object CreateFailed: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.CreateAccount>()
            .map { authManager.createAccount() }
            .onResult(
                onError = {
                    dispatchEvent(Event.CreateFailed)
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = "Create Account Failed",
                            message = it.message ?: "Something went wrong"
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnAccountCreated)
                }
            )
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.CreateAccount -> { state -> state.copy(creatingAccount = true) }
                Event.OnAccountCreated -> { state -> state.copy(creatingAccount = false) }
                Event.CreateFailed -> { state -> state.copy(creatingAccount = false) }
            }
        }
    }
}