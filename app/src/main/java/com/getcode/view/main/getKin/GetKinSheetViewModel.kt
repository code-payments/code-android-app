package com.getcode.view.main.getKin

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    betaFlags: BetaFlagsRepository,
    resources: ResourceHelper,
    tipController: TipController,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isBuyKinEnabled: Boolean = false,
        val isTipsEnabled: Boolean = false,
        val isTipCardConnected: Boolean = false,
        val tipsSubtitle: String? = null,
        val isRequestKinEnabled: Boolean = false,
        val snackbarData: SnackData? = null,
    )

    sealed interface Event {
        data class OnBetaFlagsChanged(val options: BetaOptions) : Event
        data class OnConnectionStateChanged(
            val connected: Boolean,
            val tipsSubtitle: String?,
        ) : Event

        data class ShowSnackbar(val data: SnackData?) : Event
        data object ClearSnackbar : Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnBetaFlagsChanged(it)) }
            .launchIn(viewModelScope)

        tipController.connectedAccount
            .onEach { connectedAccount ->
                val subtitle = if (connectedAccount != null) {
                    resources.getString(
                        R.string.subtitle_tips_linked_to_account,
                        connectedAccount.platform.capitalize(),
                        connectedAccount.username
                    )
                } else {
                    null
                }
                dispatchEvent(
                    Event.OnConnectionStateChanged(
                        connected = connectedAccount != null,
                        tipsSubtitle = subtitle,
                    )
                )
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFlagsChanged -> { state ->
                    state.copy(
                        isBuyKinEnabled = event.options.buyKinEnabled,
                        isTipsEnabled = event.options.tipsEnabled,
                        isRequestKinEnabled = event.options.giveRequestsEnabled,
                    )
                }

                is Event.OnConnectionStateChanged -> { state ->
                    state.copy(
                        isTipCardConnected = event.connected,
                        tipsSubtitle = event.tipsSubtitle
                    )
                }

                is Event.ShowSnackbar -> { state ->
                    state.copy(snackbarData = event.data)
                }

                Event.ClearSnackbar -> { state -> state.copy(snackbarData = null) }
            }
        }
    }
}
