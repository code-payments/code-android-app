package com.flipcash.app.myaccount.internal.settings

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.flipcash.app.appsettings.AppSettingValue
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.app.updates.ReleaseStage
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.core.R
import com.flipcash.features.myaccount.BuildConfig
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A headed group of rows. Rendered in declaration order, and dropped when it has no rows. */
internal data class SettingsSection(
    @StringRes val title: Int,
    val items: List<MenuItem<SettingsViewModel.Event>>,
)

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettingsCoordinator,
    private val featureFlags: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
    userManager: UserManager,
    versionInfo: VersionInfo,
    releaseStageProvider: ReleaseStageProvider,
    private val resources: ResourceHelper,
    private val authManager: AuthManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel<SettingsViewModel.State, SettingsViewModel.Event>(
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
        // Staff, or the version-footer override. Gates the Beta-badged rows.
        val betaUnlocked: Boolean = false,
        // Whether the version-footer override specifically is on; staff don't count toward the
        // tap-to-unlock easter egg.
        val unlockedBetaFeaturesManually: Boolean = false,
        // Whether the account holds a handle. Changing one presupposes having one.
        val usernameClaimed: Boolean = false,
        val flags: List<BetaFeature> = emptyList(),
        val versionTapCount: Int = 0,
        val appVersionInfo: VersionInfo = VersionInfo(),
        val releaseTrack: String = "",
        // Conditional rows — staff, and the handle — stay out until their real state loads, so they
        // never flash in for an account that shouldn't see them.
        val sections: List<SettingsSection> = buildSections(
            biometricsSupported = true,
            betaUnlocked = false,
            usernameClaimed = false,
            flags = emptyList(),
        ),
    )

    internal sealed interface Event {
        data class OnBetaFeaturesUnlocked(
            val unlocked: Boolean,
            val manual: Boolean = unlocked,
        ) : Event
        data class OnFeatureFlagsUpdated(val flags: List<BetaFeature>) : Event
        data class OnBiometricsSettingChanged(
            val required: Boolean,
            val supported: Boolean,
            val available: Boolean,
            @StringRes val description: Int? = null,
        ) : Event
        /** Dispatched only after the screen's biometric prompt succeeds. */
        data object OnBiometricsToggled : Event
        data class OnUsernameClaimChanged(val claimed: Boolean) : Event
        data class OnAppVersionUpdated(val versionInfo: VersionInfo) : Event
        data class OnReleaseTrackDetermined(val track: String) : Event

        data class OnEditProfile(val step: UpdateProfileStep) : Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object OpenBillPlayground : Event
        data object OnAccessKeyClicked : Event
        data object OnViewAccessKey : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
        data object OnDeleteAccountClicked : Event
        data object OnAccountDeleted : Event

        data object OnVersionInfoClicked : Event
        /**
         * A developer-mode unlock message (the tap countdown, then unlocked). The screen floats it
         * over the version row without taking taps, so the taps keep counting through it.
         */
        data class ShowDevModeToast(val message: String) : Event
    }

    init {
        dispatchEvent(Event.OnAppVersionUpdated(versionInfo))

        combine(
            featureFlags.observeOverride(),
            userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
        ) { override, isStaff -> Event.OnBetaFeaturesUnlocked(override || isStaff, manual = override) }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        featureFlags.observe()
            .onEach { dispatchEvent(Event.OnFeatureFlagsUpdated(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .map { it.userProfile?.username }
            .map { username -> !username.isNullOrBlank() }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnUsernameClaimChanged(it)) }
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

        viewModelScope.launch {
            val resolvedStage = releaseStageProvider.resolvedStage
            val label = when {
                BuildConfig.DEBUG -> "development"
                resolvedStage == null || resolvedStage == ReleaseStage.Production -> null
                else -> resolvedStage.name.lowercase()
            }
            dispatchEvent(Event.OnReleaseTrackDetermined(if (label != null) " • $label" else ""))
        }

        eventFlow
            .filterIsInstance<Event.OnBiometricsToggled>()
            .onEach {
                appSettings.update(
                    AppSettingValue.BiometricsRequired,
                    !stateFlow.value.biometricsRequired,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnVersionInfoClicked>()
            .onEach {
                if (stateFlow.value.unlockedBetaFeaturesManually) {
                    if (stateFlow.value.versionTapCount - TAP_THRESHOLD > COUNTDOWN_START) {
                        dispatchEvent(Event.ShowDevModeToast(resources.getString(R.string.toast_betaOverrideAlready)))
                    }
                    return@onEach
                }
                val remaining = TAP_THRESHOLD - stateFlow.value.versionTapCount + 1
                when {
                    remaining <= 0 -> {
                        featureFlags.enableBetaFeatures()
                        dispatchEvent(Event.ShowDevModeToast(resources.getString(R.string.toast_betaOverrideEnabled)))
                    }
                    remaining <= COUNTDOWN_START -> {
                        dispatchEvent(
                            Event.ShowDevModeToast(
                                resources.getQuantityString(R.plurals.toast_betaOverrideCountdown, remaining, remaining)
                            )
                        )
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccessKeyClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_viewAccessKey),
                    message = resources.getString(R.string.prompt_description_viewAccessKey),
                    showScrim = true,
                    showCancel = true,
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_viewAccessKey),
                            onClick = { dispatchEvent(Event.OnViewAccessKey) }
                        )
                    ),
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogOutClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_logout),
                    message = resources.getString(R.string.prompt_description_logout),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_logout)) {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.logout()
                                    .onSuccess { dispatchEvent(Event.OnLoggedOutCompletely) }
                                    .onFailure {
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToLogOut),
                                            message = resources.getString(R.string.error_description_failedToLogOut),
                                        )
                                    }
                            }
                        },
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDeleteAccountClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_deleteAccount),
                    message = resources.getString(R.string.prompt_description_deleteAccount),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_deleteAccount)) {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.deleteAndLogout()
                                    .onSuccess { dispatchEvent(Event.OnAccountDeleted) }
                                    .onFailure {
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToDeleteAccount),
                                            message = resources.getString(R.string.error_description_failedToDeleteAccount),
                                        )
                                    }
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        private const val TAP_THRESHOLD = 6
        private const val COUNTDOWN_START = 3

        /**
         * Biometrics drops out on hardware that can't offer it; Beta-badged rows need the beta
         * unlock; changing a handle needs one to already be claimed; flag-gated rows additionally
         * need their flag switched on server-side. Bill Customizer stays out, as it did on the
         * Advanced screen.
         */
        internal fun buildSections(
            biometricsSupported: Boolean,
            betaUnlocked: Boolean,
            usernameClaimed: Boolean,
            flags: List<BetaFeature>,
        ): List<SettingsSection> {
            val all = listOf(
                SettingsSection(
                    R.string.title_settingsSectionProfile,
                    listOf(ChangeDisplayName, ChangeUsername, ProfilePicture, MinimumTip),
                ),
                SettingsSection(
                    R.string.title_settingsSectionSecurity,
                    listOf(AccessKey, RequireBiometrics),
                ),
                SettingsSection(R.string.title_settingsSectionPrivacy, listOf(Blocklist)),
                SettingsSection(
                    R.string.title_advancedFeatures,
                    listOf(/* BillCustomizer, */ DeviceLogs, BetaFlags, SwitchAccount),
                ),
                SettingsSection(
                    R.string.title_settingsSectionAccount,
                    listOf(AccountInfo, LogOut, DeleteAccount),
                ),
            )
            return all
                .map { section ->
                    section.copy(
                        items = section.items
                            .filterNot { it == RequireBiometrics && !biometricsSupported }
                            .filterNot { it == ChangeUsername && !usernameClaimed }
                            .filter { it !is StaffMenuItem<*> || betaUnlocked }
                            .filter { item ->
                                val flag = item.featureFlag ?: return@filter true
                                flags.find { it.flag.key == flag.key }?.enabled == true
                            }
                    )
                }
                .filter { it.items.isNotEmpty() }
        }

        private fun State.rebuilt(
            biometricsSupported: Boolean = this.biometricsSupported,
            betaUnlocked: Boolean = this.betaUnlocked,
            usernameClaimed: Boolean = this.usernameClaimed,
            flags: List<BetaFeature> = this.flags,
        ) = copy(
            biometricsSupported = biometricsSupported,
            betaUnlocked = betaUnlocked,
            usernameClaimed = usernameClaimed,
            flags = flags,
            sections = buildSections(biometricsSupported, betaUnlocked, usernameClaimed, flags),
        )

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.rebuilt(betaUnlocked = event.unlocked)
                        .copy(unlockedBetaFeaturesManually = event.manual)
                }

                is Event.OnFeatureFlagsUpdated -> { state -> state.rebuilt(flags = event.flags) }

                is Event.OnUsernameClaimChanged -> { state ->
                    state.rebuilt(usernameClaimed = event.claimed)
                }

                is Event.OnBiometricsSettingChanged -> { state ->
                    state.rebuilt(biometricsSupported = event.supported).copy(
                        biometricsRequired = event.required,
                        biometricsAvailable = event.available,
                        biometricsDescription = event.description,
                    )
                }

                Event.OnVersionInfoClicked -> { state ->
                    state.copy(versionTapCount = state.versionTapCount + 1)
                }

                is Event.OnAppVersionUpdated -> { state -> state.copy(appVersionInfo = event.versionInfo) }
                is Event.OnReleaseTrackDetermined -> { state -> state.copy(releaseTrack = event.track) }

                Event.OnBiometricsToggled,
                is Event.OnEditProfile,
                is Event.OpenScreen,
                Event.OpenBillPlayground,
                Event.OnAccessKeyClicked,
                Event.OnViewAccessKey,
                Event.OnLogOutClicked,
                Event.OnLoggedOutCompletely,
                Event.OnDeleteAccountClicked,
                Event.OnAccountDeleted,
                is Event.ShowDevModeToast -> { state -> state }
            }
        }
    }
}
