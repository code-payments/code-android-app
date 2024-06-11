package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.model.APP_SETTINGS
import com.getcode.model.AppSetting
import com.getcode.model.PrefsBool
import com.getcode.network.repository.AppSettings
import com.getcode.network.repository.AppSettingsRepository
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class SettingItem(
    val type: AppSetting,
    val name: Int,
    val description: Int? = null,
    val icon: Int,
    val enabled: Boolean,
)

@HiltViewModel
class AppSettingsViewModel  @Inject constructor(
    appSettings: AppSettingsRepository,
) : BaseViewModel2<AppSettingsViewModel.State, AppSettingsViewModel.Event>(
    initialState = State(emptyList()),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val settings: List<SettingItem>,
    )

    sealed interface Event {
        data class UpdateSettings(val settings: List<SettingItem>) : Event
        data class SettingChanged(val setting: AppSetting, val value: Boolean): Event
    }

    init {
        appSettings.observe()
            .distinctUntilChanged()
            .map { settings ->
                APP_SETTINGS.map { setting ->
                    when (setting) {
                        PrefsBool.CAMERA_START_BY_DEFAULT -> SettingItem(
                            type = setting,
                            name = R.string.title_autoStartCamera,
                            icon = R.drawable.ic_camera_outline,
                            enabled = settings.cameraStartByDefault
                        )
                    }
                }
            }
            .onEach { settings ->
                dispatchEvent(Event.UpdateSettings(settings))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SettingChanged>()
            .map { it.setting to it.value }
            .onEach { (setting, value) ->
                appSettings.update(setting, value)
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.SettingChanged -> { state -> state }
                is Event.UpdateSettings -> { state -> state.copy(settings = event.settings) }
            }
        }
    }
}