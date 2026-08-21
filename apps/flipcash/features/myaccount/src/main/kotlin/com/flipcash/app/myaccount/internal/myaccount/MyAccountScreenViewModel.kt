package com.flipcash.app.myaccount.internal.myaccount

import androidx.lifecycle.viewModelScope
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.menu.MenuItem
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private val FullMenuList = buildList {
    // Change Display Name is deferred to its own change; the UserProfile screen it opens stays
    // wired below so re-adding the row is a one-liner.
    add(RequireBiometrics)
    add(Blocklist)
}

@HiltViewModel
internal class MyAccountScreenViewModel @Inject constructor(
    private val appSettings: AppSettingsCoordinator,
    dispatchers: DispatcherProvider,
) : BaseViewModel<MyAccountScreenViewModel.State, MyAccountScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val biometricsRequired: Boolean = false,
        // Biometrics aren't offerable on every device: the row is hidden outright when the hardware
        // isn't there, and shown-but-disabled when the hardware exists with nothing enrolled.
        val biometricsSupported: Boolean = true,
        val biometricsAvailable: Boolean = true,
        val items: List<MenuItem<Event>> = FullMenuList,
    )

    internal sealed interface Event {
        data class OnBiometricsSettingChanged(
            val required: Boolean,
            val supported: Boolean,
            val available: Boolean,
        ) : Event
        /** Dispatched only after the screen's biometric prompt succeeds. */
        data object OnBiometricsToggled : Event
        data object OnBlocklistClicked: Event
        data object OnViewBlocklist: Event
        data object OnContactMethodsClicked : Event
        data object OnViewUserProfile : Event
    }

    init {
        appSettings.settings()
            .map { items -> items.find { it.setting.type == AppSettingValue.BiometricsRequired } }
            .onEach { item ->
                item ?: return@onEach
                dispatchEvent(
                    Event.OnBiometricsSettingChanged(
                        required = item.setting.enabled,
                        supported = item.visible,
                        available = item.available,
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBiometricsToggled>()
            .onEach {
                appSettings.update(
                    AppSettingValue.BiometricsRequired,
                    !stateFlow.value.biometricsRequired,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBlocklistClicked>()
            .onEach {
                dispatchEvent(Event.OnViewBlocklist)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnContactMethodsClicked>()
            .onEach {
                dispatchEvent(Event.OnViewUserProfile)
            }.launchIn(viewModelScope)
    }

    internal companion object {
        private fun buildItemList(biometricsSupported: Boolean): List<MenuItem<Event>> =
            FullMenuList.filterNot { it == RequireBiometrics && !biometricsSupported }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnBiometricsToggled,
                Event.OnContactMethodsClicked,
                Event.OnViewUserProfile,
                Event.OnBlocklistClicked,
                Event.OnViewBlocklist -> { state -> state }

                is Event.OnBiometricsSettingChanged -> { state ->
                    state.copy(
                        biometricsRequired = event.required,
                        biometricsSupported = event.supported,
                        biometricsAvailable = event.available,
                        items = buildItemList(event.supported),
                    )
                }
            }
        }
    }
}
