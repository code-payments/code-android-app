package xyz.flipchat.app.features.settings

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val chatsController: ChatsController,
    userManager: UserManager,
) : BaseViewModel2<SettingsViewModel.State, SettingsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val isStaff: Boolean = false,
    )

    sealed interface Event {
        data class OnStaffEmployed(val enabled: Boolean) : Event
    }

    init {
        userManager.state
            .mapNotNull { it.flags }
            .map { it.isStaff }
            .onEach { dispatchEvent(Event.OnStaffEmployed(it)) }
            .launchIn(viewModelScope)
    }

    fun logout(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.logout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }

    fun deleteAccount(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.deleteAndLogout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnStaffEmployed -> { state -> state.copy(isStaff = event.enabled) }
            }
        }
    }
}