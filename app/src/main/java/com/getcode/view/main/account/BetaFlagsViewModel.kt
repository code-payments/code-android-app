package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class BetaFlagsViewModel @Inject constructor(
    betaFlags: BetaFlagsRepository,
    prefRepository: PrefRepository,
) : BaseViewModel2<BetaFlagsViewModel.State, BetaFlagsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val showNetworkDropOff: Boolean = false,
        val canViewBuckets: Boolean = false,
        val isVibrateOnScan: Boolean = false,
        val currencySelectionBalanceEnabled: Boolean = false,
        val displayErrors: Boolean = false,
        val giveRequestsEnabled: Boolean = false,
        val buyKinEnabled: Boolean = false,
        val establishCodeRelationship: Boolean = false,
        val chatUnsubEnabled: Boolean = false,
        val tipsEnabled: Boolean = false,
        val tipsChatEnabled: Boolean = false,
        val tipsChatCashEnabled: Boolean = false,
        val kadoWebViewEnabled: Boolean = false,
    )

    sealed interface Event {
        data class UpdateSettings(val settings: BetaOptions) : Event
        data class Toggle(val setting: PrefsBool, val state: Boolean): Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { settings ->
                dispatchEvent(Event.UpdateSettings(settings))
            }.launchIn(viewModelScope)


        eventFlow
            .filterIsInstance<Event.Toggle>()
            .onEach {
                prefRepository.set(
                    it.setting,
                    it.state
                )
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateSettings -> { state ->
                    with(event.settings) {
                        state.copy(
                            showNetworkDropOff = showNetworkDropOff,
                            canViewBuckets = canViewBuckets,
                            isVibrateOnScan = tickOnScan,
                            currencySelectionBalanceEnabled = balanceCurrencySelectionEnabled,
                            displayErrors = displayErrors,
                            giveRequestsEnabled = giveRequestsEnabled,
                            buyKinEnabled = buyModuleEnabled,
                            chatUnsubEnabled = chatUnsubEnabled,
                            tipsEnabled = tipsEnabled,
                            tipsChatEnabled = tipsChatEnabled,
                            tipsChatCashEnabled = tipsChatCashEnabled,
                            kadoWebViewEnabled = kadoWebViewEnabled,
                        )
                    }
                }

                is Event.Toggle -> { state -> state }
            }
        }
    }
}