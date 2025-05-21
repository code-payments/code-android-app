package com.flipcash.app.menu.internal

import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.menu.R
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FullMenuList = buildList {
    add(Deposit)
    add(Withdraw)
    add(MyAccount)
    add(AppSettings)
    add(SwitchAccount)
    add(Labs)
    add(LogOut)
}

@HiltViewModel
internal class MenuScreenViewModel @Inject constructor(
    private val resources: ResourceHelper,
    userManager: UserManager,
    authManager: AuthManager,
    versionInfo: VersionInfo,
    mnemonicManager: MnemonicManager,
    featureFlags: FeatureFlagController,
) :
    BaseViewModel2<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent
    ) {
    data class State(
        val items: List<MenuItem<Event>> = FullMenuList,
        val logoTapCount: Int = 0,
        val isStaff: Boolean = false,
        val unlockedBetaFeaturesManually: Boolean = false,
        val appVersionInfo: VersionInfo = VersionInfo(),
    )

    sealed interface Event {
        data object OnLogoTapped: Event
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean): Event
        data class OnAppVersionUpdated(val versionInfo: VersionInfo) : Event
        data class OnStaffUserDetermined(val staff: Boolean) : Event
        data class OpenScreen(val screen: NavScreenProvider) : Event
        data object OnSwitchAccountsClicked : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
        data class OnSwitchAccountTo(val entropy: String): Event
    }

    init {
        userManager.state
            .filter { it.authState is AuthState.LoggedIn }
            .mapNotNull { it.flags }
            .map { it.isStaff }
            .onEach {
                dispatchEvent(Event.OnAppVersionUpdated(versionInfo))
                dispatchEvent(Event.OnStaffUserDetermined(it))
            }.launchIn(viewModelScope)

        featureFlags.observeOverride()
            .filter { userManager.authState is AuthState.LoggedIn }
            .onEach { dispatchEvent(Event.OnBetaFeaturesUnlocked(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogoTapped>()
            .map { stateFlow.value.logoTapCount }
            .filter { it > TAP_THRESHOLD }
            .filterNot { stateFlow.value.unlockedBetaFeaturesManually }
            .onEach { featureFlags.enableBetaFeatures() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSwitchAccountsClicked>()
            .map {
                authManager.selectAccount()
                    .fold(
                        onSuccess = {
                            authManager.logoutAndSwitchAccount(
                                mnemonicManager.getEncodedBase64(it)
                            )
                        },
                        onFailure = { Result.failure(it) }
                    )
            }.onResult(
                onError = {

                },
                onSuccess = { dispatchEvent(Event.OnSwitchAccountTo(it)) }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogOutClicked>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_logout),
                        subtitle = resources.getString(R.string.prompt_description_logout),
                        showScrim = true,
                        positiveText = resources.getString(R.string.action_logout),
                        tertiaryText = resources.getString(R.string.action_cancel),
                        onPositive = {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.logout()
                                    .onSuccess {
                                        dispatchEvent(Event.OnLoggedOutCompletely)
                                    }
                                    .onFailure {
                                        TopBarManager.showMessage(
                                            TopBarManager.TopBarMessage(
                                                title = resources.getString(R.string.error_title_failedToLogOut),
                                                message = resources.getString(R.string.error_description_failedToLogOut),
                                            )
                                        )
                                    }
                            }
                        }
                    )
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        private const val TAP_THRESHOLD = 6

        private fun buildItemList(isStaff: Boolean, overrode: Boolean): List<MenuItem<Event>> {
            return if (isStaff || overrode) {
                FullMenuList
            } else {
                FullMenuList.filterNot { it.isStaffOnly }
            }
        }

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnLogoTapped -> { state ->
                    state.copy(logoTapCount = state.logoTapCount + 1)
                }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        unlockedBetaFeaturesManually = event.unlocked,
                        items = buildItemList(state.isStaff, event.unlocked)
                    )
                }

                is Event.OnAppVersionUpdated -> { state ->
                    state.copy(appVersionInfo = event.versionInfo)
                }

                is Event.OnStaffUserDetermined -> { state ->
                    state.copy(
                        isStaff = event.staff,
                        items = buildItemList(event.staff, state.unlockedBetaFeaturesManually)
                    )
                }

                Event.OnLogOutClicked,
                Event.OnSwitchAccountsClicked,
                is Event.OpenScreen,
                Event.OnLoggedOutCompletely,
                is Event.OnSwitchAccountTo -> { state -> state }
            }
        }
    }
}