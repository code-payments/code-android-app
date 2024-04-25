package com.getcode.view.main.getKin

import androidx.lifecycle.viewModelScope
import com.getcode.model.PrefsBool
import com.getcode.network.TipController
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.ui.components.SnackData
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    betaFlags: BetaFlagsRepository,
    tipController: TipController,
    prefRepository: PrefRepository,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isBuyModuleEnabled: Boolean = false,
        val isTipsEnabled: Boolean = false,
        val isTipCardConnected: Boolean = false,
        val isRequestKinEnabled: Boolean = false,
        val isBuyModuleAvailable: Boolean = false,
        val snackbarData: SnackData? = null,
    )

    sealed interface Event {
        data class OnBetaFlagsChanged(val options: BetaOptions) : Event
        data class OnConnectionStateChanged(
            val connected: Boolean,
        ) : Event

        data class OnBuyModuleAvailabilityChanged(val available: Boolean): Event

        data class ShowSnackbar(val data: SnackData?) : Event
        data object ClearSnackbar : Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnBetaFlagsChanged(it)) }
            .launchIn(viewModelScope)

        prefRepository
            .observeOrDefault(PrefsBool.BUY_MODULE_AVAILABLE, false)
            .distinctUntilChanged()
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnBuyModuleAvailabilityChanged(it)) }
            .launchIn(viewModelScope)

        tipController.connectedAccount
            .onEach { connectedAccount ->
                dispatchEvent(
                    Event.OnConnectionStateChanged(
                        connected = connectedAccount != null,
                    )
                )
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFlagsChanged -> { state ->
                    state.copy(
                        isBuyModuleEnabled = event.options.buyModuleEnabled,
                        isTipsEnabled = event.options.tipsEnabled,
                        isRequestKinEnabled = event.options.giveRequestsEnabled,
                    )
                }

                is Event.OnConnectionStateChanged -> { state ->
                    state.copy(
                        isTipCardConnected = event.connected,
                    )
                }

                is Event.OnBuyModuleAvailabilityChanged -> { state -> state.copy(isBuyModuleAvailable = event.available) }

                is Event.ShowSnackbar -> { state ->
                    state.copy(snackbarData = event.data)
                }

                Event.ClearSnackbar -> { state -> state.copy(snackbarData = null) }
            }
        }
    }
}
