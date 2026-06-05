package com.flipcash.app.lab.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.userflags.UserFlagsCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabsScreenViewModel @Inject constructor(
    userFlags: UserFlagsCoordinator,
) : ViewModel() {

    val isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue }
        .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = false)
}
