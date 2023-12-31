package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.model.PrefsBool
import com.getcode.network.repository.AccountDebugRepository
import com.getcode.network.repository.AccountDebugSettings
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountDebugOptionsViewModel @Inject constructor(
    accountDebugRepository: AccountDebugRepository,
    prefRepository: PrefRepository,
) : BaseViewModel2<AccountDebugOptionsViewModel.State, AccountDebugOptionsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val isDebugBuckets: Boolean = false,
        val isVibrateOnScan: Boolean = false,
        val isDisplayErrors: Boolean = false,
        val isRemoteSendEnabled: Boolean = false,
        val isIncentivesEnabled: Boolean = false,
    )

    sealed interface Event {
        data class UpdateSettings(val settings: AccountDebugSettings) : Event

        data class ShowErrors(val display: Boolean) : Event
        data class SetVibrateOnScan(val vibrate: Boolean) : Event
        data class UseDebugBuckets(val enabled: Boolean) : Event
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
                prefRepository.set(PrefsBool.IS_DEBUG_DISPLAY_ERRORS, it)
                ErrorUtils.setDisplayErrors(it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SetVibrateOnScan>()
            .map { it.vibrate }
            .onEach {
                prefRepository.set(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, it)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UseDebugBuckets>()
            .map { it.enabled }
            .onEach {
                prefRepository.set(PrefsBool.IS_DEBUG_BUCKETS, it)
            }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateSettings -> { state ->
                    with(event.settings) {
                        state.copy(
                            isDebugBuckets = isDebugBuckets,
                            isVibrateOnScan = isVibrateOnScan,
                            isDisplayErrors = isDisplayErrors,
                            isRemoteSendEnabled = isRemoteSendEnabled,
                            isIncentivesEnabled = isIncentivesEnabled,
                        )
                    }
                }

                is Event.UseDebugBuckets,
                is Event.SetVibrateOnScan,
                is Event.ShowErrors -> { state -> state }
            }
        }
    }
}