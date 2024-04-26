package com.getcode.view.main.getKin

import androidx.lifecycle.viewModelScope
import com.getcode.model.BuyModuleFeature
import com.getcode.model.Feature
import com.getcode.model.RequestKinFeature
import com.getcode.model.TipCardFeature
import com.getcode.network.TipController
import com.getcode.network.repository.FeatureRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.ui.components.SnackData
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    features: FeatureRepository,
    tipController: TipController,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val buyModule: Feature = BuyModuleFeature(),
        val tips: Feature = TipCardFeature(),
        val requestKin: Feature = RequestKinFeature(),
        val isTipCardConnected: Boolean = false,
        val snackbarData: SnackData? = null,
    )

    sealed interface Event {
        data class OnBuyModuleStateChanged(val module: Feature) : Event
        data class OnTipsStateChanged(val module: Feature) : Event
        data class OnRequestKinStateChanged(val module: Feature) : Event
        data class OnConnectionStateChanged(
            val connected: Boolean,
        ) : Event

        data class ShowSnackbar(val data: SnackData?) : Event
        data object ClearSnackbar : Event
    }

    init {
        features.buyModule
            .onEach { dispatchEvent(Event.OnBuyModuleStateChanged(it)) }
            .launchIn(viewModelScope)

        features.tipCards
            .onEach { dispatchEvent(Event.OnTipsStateChanged(it)) }
            .launchIn(viewModelScope)

        features.requestKin
            .onEach { dispatchEvent(Event.OnRequestKinStateChanged(it)) }
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
                is Event.OnBuyModuleStateChanged -> { state ->
                    state.copy(
                        buyModule = event.module
                    )
                }
                is Event.OnTipsStateChanged -> { state ->
                    state.copy(
                        tips = event.module
                    )
                }
                is Event.OnRequestKinStateChanged -> { state ->
                    state.copy(
                        requestKin = event.module
                    )
                }

                is Event.OnConnectionStateChanged -> { state ->
                    state.copy(
                        isTipCardConnected = event.connected,
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
