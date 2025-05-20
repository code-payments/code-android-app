package xyz.flipchat.app.features.login

import androidx.lifecycle.viewModelScope
import com.getcode.manager.TopBarManager
import com.getcode.services.utils.onSuccessWithDelay
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
    betaFlags: Labs,
) : BaseViewModel2<LoginViewModel.State, LoginViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val followerModeEnabled: Boolean = false,
        val creatingAccount: LoadingSuccessState = LoadingSuccessState(),
        val logoTapCount: Int = 0,
        val betaOptionsVisible: Boolean = false,
    )

    sealed interface Event {
        data object OnLogoTapped: Event
        data object BetaOptionsUnlocked: Event
        data class OnFollowerModeEnabled(val enabled: Boolean): Event
        data object CreateAccount: Event
        data object OnAccountCreated: Event
        data object CreateFailed: Event
    }

    init {
        betaFlags.observe(Lab.FollowerMode)
            .onEach { dispatchEvent(Event.OnFollowerModeEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogoTapped>()
            .map { stateFlow.value.logoTapCount }
            .filter { it >= TAP_THRESHOLD }
            .filterNot { stateFlow.value.betaOptionsVisible }
            .onEach { dispatchEvent(Event.BetaOptionsUnlocked) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CreateAccount>()
            .map {
                authManager.createAccount()
                    .onFailure {
                        dispatchEvent(Event.CreateFailed)
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                title = "Create Account Failed",
                                message = it.localizedMessage ?: "Something went wrong"
                            )
                        )
                    }.onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnAccountCreated)
                    }
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        private const val TAP_THRESHOLD = 6
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.CreateAccount -> { state -> state.copy(creatingAccount = LoadingSuccessState(loading = true)) }
                Event.OnAccountCreated -> { state -> state.copy(creatingAccount = LoadingSuccessState(loading = false, success = true)) }
                Event.CreateFailed -> { state -> state.copy(creatingAccount = LoadingSuccessState(loading = false)) }
                is Event.OnFollowerModeEnabled -> { state -> state.copy(followerModeEnabled = event.enabled) }
                is Event.BetaOptionsUnlocked -> { state -> state.copy(betaOptionsVisible = true) }
                is Event.OnLogoTapped -> { state ->
                    if (state.logoTapCount >= TAP_THRESHOLD) state
                    else state.copy(logoTapCount = state.logoTapCount + 1)
                }
            }
        }
    }
}