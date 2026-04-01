package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    userManager: UserManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val preferredOnRampProvider: OnRampProvider? = null,
    )

    sealed interface Event {
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider?) : Event

        data object OpenCurrencySelection : Event

        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.preferredOnRampProvider }
            .onEach { provider ->
                dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider))
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}