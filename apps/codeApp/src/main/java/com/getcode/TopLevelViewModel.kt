package com.getcode

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.manager.AuthManager
import com.getcode.manager.TopBarManager
import com.getcode.services.model.PrefsBool
import com.getcode.network.repository.AppSettingsRepository
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.FeatureRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val appSettings: AppSettingsRepository,
    betaFlagsRepository: BetaFlagsRepository,
    features: FeatureRepository,
    resources: ResourceHelper,
) : BaseViewModel(resources) {

    private val _eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()
    val eventFlow: SharedFlow<Event> = _eventFlow.asSharedFlow()

    private val betaFlags = betaFlagsRepository.observe()

    private val requireBiometrics = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            requireBiometrics.value = appSettings.get(PrefsBool.REQUIRE_BIOMETRICS)
        }
    }

    val state = combine(
        betaFlags,
        features.buyModule.map { it.available },
        requireBiometrics,
    ) { beta, buykinAvailable, requireBiometrics ->
        State(beta, buykinAvailable, requireBiometrics)
    } .stateIn(viewModelScope, started = SharingStarted.Eagerly, State.Empty)

    data class State(
        val betaFlags: BetaOptions,
        val buyModuleAvailable: Boolean,
        val requireBiometrics: Boolean?,
    ) {
        companion object {
            val Empty = State(
                betaFlags = BetaOptions.Defaults,
                buyModuleAvailable = false,
                requireBiometrics = null
            )
        }
    }

    sealed interface Event {
        data object LogoutRequested: Event
        data object LogoutCompleted: Event
    }

    fun onResume() {
        viewModelScope.launch {
            requireBiometrics.value = appSettings.get(PrefsBool.REQUIRE_BIOMETRICS)
        }
    }

    fun onMissingBiometrics() {
        // biometrics required by user, but now not enrolled
        // show a top bar error and let them in
        TopBarManager.showMessage(
            getString(R.string.error_title_missingBiometrics),
            getString(R.string.error_description_missingBiometrics)
        )
        appSettings.update(setting = PrefsBool.REQUIRE_BIOMETRICS, value = false, fromUser = false)
        requireBiometrics.value = false
    }

    fun logout(activity: Activity, onComplete: () -> Unit = {}) {
        _eventFlow.tryEmit(Event.LogoutRequested)
        authManager.logout(activity) {
            _eventFlow.tryEmit(Event.LogoutCompleted)
            onComplete()
        }
    }
}
