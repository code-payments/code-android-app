package com.flipcash.app.myaccount.internal.myaccount

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private val FullMenuList = buildList {
    add(ChangeDisplayName)
    add(RequireBiometrics)
    add(Blocklist)
    add(UserProfile)
}

@HiltViewModel
internal class MyAccountScreenViewModel @Inject constructor(
    private val appSettings: AppSettingsCoordinator,
    featureFlagController: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
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
        // Why the row can't act, when it can't — e.g. the hardware is there with nothing enrolled.
        @StringRes val biometricsDescription: Int? = null,
        val betaUnlocked: Boolean = false,
        // Staff-only rows stay out until the real flag state loads, so they never flash in.
        val items: List<MenuItem<Event>> = FullMenuList.filterNot { it is StaffMenuItem },
    )

    internal sealed interface Event {
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data class OnBiometricsSettingChanged(
            val required: Boolean,
            val supported: Boolean,
            val available: Boolean,
            @StringRes val description: Int? = null,
        ) : Event
        /** Dispatched only after the screen's biometric prompt succeeds. */
        data object OnBiometricsToggled : Event
        data object OnChangeDisplayNameClicked : Event
        data object OnEditDisplayName : Event
        data object OnBlocklistClicked: Event
        data object OnViewBlocklist: Event
        data object OnContactMethodsClicked : Event
        data object OnViewUserProfile : Event
    }

    init {
        combine(
            featureFlagController.observeOverride(),
            userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
        ) { override, isStaff -> override || isStaff }
            .onEach { dispatchEvent(Event.OnBetaFeaturesUnlocked(it)) }
            .launchIn(viewModelScope)

        appSettings.settings()
            .map { items -> items.find { it.setting.type == AppSettingValue.BiometricsRequired } }
            .onEach { item ->
                item ?: return@onEach
                dispatchEvent(
                    Event.OnBiometricsSettingChanged(
                        required = item.setting.enabled,
                        supported = item.visible,
                        available = item.available,
                        description = item.description,
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
            .filterIsInstance<Event.OnChangeDisplayNameClicked>()
            .onEach {
                dispatchEvent(Event.OnEditDisplayName)
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
        /** Biometrics drops out on hardware that can't offer it; staff rows need the beta unlock. */
        private fun buildItemList(
            biometricsSupported: Boolean,
            betaUnlocked: Boolean,
        ): List<MenuItem<Event>> = FullMenuList
            .filterNot { it == RequireBiometrics && !biometricsSupported }
            .filter { it !is StaffMenuItem || betaUnlocked }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnBiometricsToggled,
                Event.OnChangeDisplayNameClicked,
                Event.OnEditDisplayName,
                Event.OnContactMethodsClicked,
                Event.OnViewUserProfile,
                Event.OnBlocklistClicked,
                Event.OnViewBlocklist -> { state -> state }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        betaUnlocked = event.unlocked,
                        items = buildItemList(
                            biometricsSupported = state.biometricsSupported,
                            betaUnlocked = event.unlocked,
                        ),
                    )
                }

                is Event.OnBiometricsSettingChanged -> { state ->
                    state.copy(
                        biometricsRequired = event.required,
                        biometricsSupported = event.supported,
                        biometricsAvailable = event.available,
                        biometricsDescription = event.description,
                        items = buildItemList(
                            biometricsSupported = event.supported,
                            betaUnlocked = state.betaUnlocked,
                        ),
                    )
                }
            }
        }
    }
}
