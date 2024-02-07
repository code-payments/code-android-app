package com.getcode

import android.app.Activity
import com.getcode.manager.AuthManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    resources: ResourceHelper,
) : BaseViewModel(resources) {

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

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
