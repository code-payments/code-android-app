package com.getcode.view.main.getKin

import androidx.compose.material.SnackbarDuration
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.network.TipController
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.ui.components.SnackData
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    resources: ResourceHelper,
    betaFlags: BetaFlagsRepository,
    tipController: TipController,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isTipsEnabled: Boolean = false,
        val isTipCardConnected: Boolean = false,
        val isRequestKinEnabled: Boolean = false,
        val snackbarData: SnackData? = null,
    )

    sealed interface Event {
        data class OnBetaFlagsChanged(val options: BetaOptions) : Event
        data class OnConnectionStateChanged(val connected: Boolean): Event
        data class ShowSnackbar(val data: SnackData?) : Event
        data object ClearSnackbar : Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnBetaFlagsChanged(it)) }
            .launchIn(viewModelScope)

        combine(
            tipController.connectedAccount,
            tipController.showTwitterSplat
        ) { username, show ->
            dispatchEvent(Event.OnConnectionStateChanged(!username.isNullOrEmpty()))
            if (!username.isNullOrEmpty()) {
                if (show) {
                    dispatchEvent(
                        Event.ShowSnackbar(
                            data = SnackData(
                                message = resources.getString(
                                    R.string.subtitle_xAccountConnected,
                                    username
                                ),
                                actionLabel = resources.getString(R.string.action_shareTipCard),
                                duration = SnackbarDuration.Indefinite
                            )
                        )
                    )
                }
            }
        }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFlagsChanged -> { state ->
                    state.copy(
                        isTipsEnabled = event.options.tipsEnabled,
                        isRequestKinEnabled = event.options.giveRequestsEnabled,
                    )
                }

                is Event.OnConnectionStateChanged -> { state ->
                    state.copy(isTipCardConnected = event.connected)
                }

                is Event.ShowSnackbar -> { state ->
                    state.copy(snackbarData = event.data)
                }

                Event.ClearSnackbar -> { state -> state.copy(snackbarData = null) }
            }
        }
    }
}
