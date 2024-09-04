package com.getcode.view.main.account

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.util.Pacman
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
    pacman: Pacman,
) : BaseViewModel2<BetaOptions, BetaFlagsViewModel.Event>(
    initialState = BetaOptions.Defaults,
    updateStateForEvent = updateStateForEvent
) {
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

                when (it.setting) {
                    PrefsBool.SHARE_TWEET_TO_TIP -> {
                        pacman.enableTweetShare(it.state)
                    }
                    PrefsBool.DISPLAY_ERRORS -> {
                        ErrorUtils.setDisplayErrors(it.state)
                    }
                    else -> Unit
                }
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((BetaOptions) -> BetaOptions) = { event ->
            when (event) {
                is Event.UpdateSettings -> { _ ->
                    event.settings
                }

                is Event.Toggle -> { state -> state }
            }
        }
    }
}