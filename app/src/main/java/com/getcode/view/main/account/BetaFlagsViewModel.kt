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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class BetaFlagsViewModel @Inject constructor(
    accountDebugRepository: BetaFlagsRepository,
    prefRepository: PrefRepository,
) : BaseViewModel2<BetaFlagsViewModel.State, BetaFlagsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val showNetworkDropOff: Boolean = false,
        val canViewBuckets: Boolean = false,
        val isVibrateOnScan: Boolean = false,
        val debugScanTimesEnabled: Boolean = false,
        val displayErrors: Boolean = false,
        val remoteSendEnabled: Boolean = false,
        val giveRequestsEnabled: Boolean = false,
    )

    sealed interface Event {
        data class UpdateSettings(val settings: BetaOptions) : Event

        data class ShowErrors(val display: Boolean) : Event
        data class ShowNetworkDropOff(val show: Boolean): Event
        data class SetLogScanTimes(val log: Boolean) : Event
        data class SetVibrateOnScan(val vibrate: Boolean) : Event
        data class UseDebugBuckets(val enabled: Boolean) : Event
        data class EnableGiveRequests(val enabled: Boolean): Event
    }

    init {
        accountDebugRepository.observe()
            .distinctUntilChanged()
            .onEach { settings ->
                dispatchEvent(Event.UpdateSettings(settings))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShowErrors>()
            .map { it.display }
            .onEach {
                prefRepository.set(PrefsBool.DISPLAY_ERRORS, it)
                ErrorUtils.setDisplayErrors(it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SetVibrateOnScan>()
            .map { it.vibrate }
            .onEach {
                prefRepository.set(PrefsBool.VIBRATE_ON_SCAN, it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ShowNetworkDropOff>()
            .map { it.show }
            .onEach {
                prefRepository.set(PrefsBool.SHOW_CONNECTIVITY_STATUS, it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SetLogScanTimes>()
            .map { it.log }
            .onEach {
                prefRepository.set(PrefsBool.LOG_SCAN_TIMES, it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UseDebugBuckets>()
            .map { it.enabled }
            .onEach {
                prefRepository.set(PrefsBool.BUCKET_DEBUGGER_ENABLED, it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.EnableGiveRequests>()
            .map { it.enabled }
            .onEach {
                prefRepository.set(PrefsBool.GIVE_REQUESTS_ENABLED, it)
            }
            .launchIn(viewModelScope)
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
                            debugScanTimesEnabled = debugScanTimesEnabled,
                            displayErrors = displayErrors,
                            remoteSendEnabled = remoteSendEnabled,
                            giveRequestsEnabled = giveRequestsEnabled,
                        )
                    }
                }

                is Event.EnableGiveRequests,
                is Event.ShowNetworkDropOff,
                is Event.UseDebugBuckets,
                is Event.SetLogScanTimes,
                is Event.SetVibrateOnScan,
                is Event.ShowErrors -> { state -> state }
            }
        }
    }
}