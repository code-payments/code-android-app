package com.getcode

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.manager.AuthManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    betaFlagsRepository: BetaFlagsRepository,
    prefRepository: PrefRepository,
    resources: ResourceHelper,
) : BaseViewModel(resources) {

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    private val betaFlags = betaFlagsRepository.observe()

    val state = combine(
        betaFlags,
        prefRepository.observeOrDefault(PrefsBool.BUY_MODULE_AVAILABLE, false)
    ) { beta, buykinAvailable ->
        State(beta, buykinAvailable)
    } .stateIn(viewModelScope, started = SharingStarted.Eagerly, State.Empty)

    data class State(
        val betaFlags: BetaOptions,
        val buyModuleAvailable: Boolean,
    ) {
        companion object {
            val Empty = State(BetaOptions.Defaults, false)
        }
    }

    sealed interface Event {
        data object LogoutRequested: Event
        data object LogoutCompleted: Event
    }

    fun logout(activity: Activity, onComplete: () -> Unit = {}) {
        _eventFlow.tryEmit(Event.LogoutRequested)
        authManager.logout(activity) {
            _eventFlow.tryEmit(Event.LogoutCompleted)
            onComplete()
        }
    }
}
