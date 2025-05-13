package com.flipcash.app.deposit.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.services.user.UserManager
import com.getcode.solana.keys.base58
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    userManager: UserManager
) : BaseViewModel2<DepositViewModel.State, DepositViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    internal data class State(
        val depositAddress: String = "",
        val isCopied: Boolean = false
    )

    internal sealed interface Event {
        data class OnDepositAddressChanged(val address: String) : Event
        data object CopyAddress : Event
        data class SetCopied(val isCopied: Boolean) : Event
    }

    init {
        userManager.state
            .mapNotNull { it.cluster?.depositAddress?.base58() }
            .onEach { address -> dispatchEvent(Event.OnDepositAddressChanged(address)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyAddress>()
            .onEach {
                dispatchEvent(Event.SetCopied(true))
                delay(2.seconds)
                dispatchEvent(Event.SetCopied(false))
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnDepositAddressChanged -> { state ->
                    state.copy(depositAddress = event.address)
                }

                is Event.CopyAddress -> { state -> state }
                is Event.SetCopied -> { state -> state.copy(isCopied = event.isCopied) }
            }
        }
    }
}