package com.flipcash.app.lab.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabsScreenViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
) : ViewModel() {

    val isLoggedIn = userManager
        .state.map { it.authState }
        .filterIsInstance<AuthState.LoggedInWithUser>()
        .map { true }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    val isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue }
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = false)
}
