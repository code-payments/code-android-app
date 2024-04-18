package com.getcode

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.manager.AuthManager
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    betaFlagsRepository: BetaFlagsRepository,
    resources: ResourceHelper,
) : BaseViewModel(resources) {

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    val betaFlags = betaFlagsRepository.observe()
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, BetaOptions())

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
