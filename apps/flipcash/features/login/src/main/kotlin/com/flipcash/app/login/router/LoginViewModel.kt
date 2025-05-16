package com.flipcash.app.login.router

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.internal.extensions.onSuccessWithDelay
import com.getcode.manager.TopBarManager
import com.getcode.utils.encodeBase64
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import org.kin.sdk.base.tools.Base58
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
) : BaseViewModel2<LoginViewModel.State, LoginViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val followerModeEnabled: Boolean = false,
        val creatingAccount: LoadingSuccessState = LoadingSuccessState(),
        val loggingIn: LoadingSuccessState = LoadingSuccessState(),
        val logoTapCount: Int = 0,
        val betaOptionsVisible: Boolean = false,
    )

    sealed interface Event {
        data object OnLogoTapped : Event
        data object BetaOptionsUnlocked : Event
        data object CreateAccount : Event
        data class LogIn(val seed: String, val fromDeeplink: Boolean = false) : Event
        data class FacilitateLogin(val entropyB64: String) : Event
        data object LoggedInSuccessfully : Event
        data object LogInFailed : Event
        data object OnAccountCreated : Event
        data object CreateFailed : Event
    }

    init {
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
                                message = it.message ?: "Something went wrong"
                            )
                        )
                    }.onSuccessWithDelay(2.seconds) {
                        dispatchEvent(Event.OnAccountCreated)
                    }
            }
            .launchIn(viewModelScope)

        // deeplinks are base58 encoded
        eventFlow
            .filterIsInstance<Event.LogIn>()
            .filter { it.fromDeeplink }
            .map { it.seed }
            .mapNotNull { seed ->
                val entropy = runCatching {
                    Base58.decode(seed)
                }.onFailure {
                    dispatchEvent(Event.LogInFailed)
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = "Login Failed",
                            message = "Something went wrong"
                        )
                    )
                    return@mapNotNull null
                }.getOrNull()

                val entropyB64 = entropy?.encodeBase64()
                if (seed.isBlank() || entropy?.size != 16) {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = "Login Failed",
                            message = "Something went wrong"
                        )
                    )
                    return@mapNotNull null
                }

                entropyB64
            }.onEach { entropyB4 -> dispatchEvent(Event.FacilitateLogin(entropyB4)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LogIn>()
            .filterNot { it.fromDeeplink }
            .map { it.seed }
            .onEach { entropyB64 -> dispatchEvent(Event.FacilitateLogin(entropyB64)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.FacilitateLogin>()
            .map { it.entropyB64 }
            .onEach { entropyB64 ->
                authManager.login(
                    entropyB64 = entropyB64,
                    // treat deep links and account switches as if they came from the selection screen
                    isFromSelection = true
                ).onFailure {
                    dispatchEvent(Event.LogInFailed)
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = "Login Failed",
                            message = it.message ?: "Something went wrong"
                        )
                    )
                }.onSuccess {
                    dispatchEvent(Event.LoggedInSuccessfully)
                }
            }.launchIn(viewModelScope)

    }

    internal companion object {
        private const val TAP_THRESHOLD = 6
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.CreateAccount -> { state ->
                    state.copy(
                        creatingAccount = LoadingSuccessState(
                            loading = true
                        )
                    )
                }

                Event.OnAccountCreated -> { state ->
                    state.copy(
                        creatingAccount = LoadingSuccessState(
                            loading = false,
                            success = true
                        )
                    )
                }

                Event.CreateFailed -> { state ->
                    state.copy(
                        creatingAccount = LoadingSuccessState(
                            loading = false
                        )
                    )
                }

                is Event.BetaOptionsUnlocked -> { state -> state.copy(betaOptionsVisible = true) }
                is Event.OnLogoTapped -> { state ->
                    if (state.logoTapCount >= TAP_THRESHOLD) state
                    else state.copy(logoTapCount = state.logoTapCount + 1)
                }

                is Event.LogIn -> { state -> state.copy(loggingIn = LoadingSuccessState(loading = true)) }
                is Event.FacilitateLogin -> { state -> state }
                is Event.LoggedInSuccessfully -> { state ->
                    state.copy(
                        loggingIn = LoadingSuccessState(
                            loading = false,
                            success = true
                        )
                    )
                }

                is Event.LogInFailed -> { state ->
                    state.copy(
                        loggingIn = LoadingSuccessState(
                            loading = false
                        )
                    )
                }
            }
        }
    }
}