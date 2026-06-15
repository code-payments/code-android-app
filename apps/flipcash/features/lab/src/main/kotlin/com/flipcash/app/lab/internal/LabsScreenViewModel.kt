package com.flipcash.app.lab.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.core.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabsScreenViewModel @Inject constructor(
    userFlags: UserFlagsCoordinator,
    private val featureFlagController: FeatureFlagController,
    private val toastController: SystemToastController,
) : ViewModel() {

    val isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue }
        .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = false)

    val betaOverride = featureFlagController.observeOverride()

    fun disableBetaFeatures() {
        featureFlagController.disableBetaFeatures()
        toastController.showToast(R.string.toast_betaOverrideDisabled)
    }
}
