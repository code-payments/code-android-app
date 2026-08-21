package com.flipcash.app.advanced.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.getcode.opencode.managers.MnemonicManager
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FullMenuList = buildList {
    add(AccessKey)
    add(BetaFlags)
    add(DeviceLogs)
//    add(BillCustomizer)
    add(SwitchAccount)
    add(LogOut)
    add(DeleteAccount)
}

@HiltViewModel
internal class AdvancedFeaturesScreenViewModel @Inject constructor(
    featureFlagController: FeatureFlagController,
    userFlags: UserFlagsCoordinator,
    resources: ResourceHelper,
    authManager: AuthManager,
    mnemonicManager: MnemonicManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel<AdvancedFeaturesScreenViewModel.State, AdvancedFeaturesScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val isBetaEnabled: Boolean = false,
        val flags: List<BetaFeature> = emptyList(),
        // Default hides staff-only AND flag-gated items until the real state loads, so a beta-gated
        // row never flashes before its flag resolves.
        val items: List<MenuItem<Event>> =
            FullMenuList.filterNot { it is StaffMenuItem || it.featureFlag != null },
    )

    sealed interface Event {
        data class OnBetaFeaturesUnlocked(
            val unlocked: Boolean,
            val flags: List<BetaFeature> = emptyList(),
        ) : Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object OnSwitchAccountsClicked : Event
        data class OnSwitchAccountTo(val entropy: String) : Event
        data object OpenBillPlayground : Event
        data object OnAccessKeyClicked : Event
        data object OnViewAccessKey : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
        data object OnDeleteAccountClicked : Event
        data object OnAccountDeleted : Event
    }

    init {
        combine(
            featureFlagController.observeOverride(),
            userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
            featureFlagController.observe(),
        ) { override, isStaff, flags ->
            dispatchEvent(Event.OnBetaFeaturesUnlocked(override || isStaff, flags))
        }.launchIn(viewModelScope)

        // Hands off to Google's Password Manager to pick another access key, then re-logs in as it.
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
                onError = { },
                onSuccess = { dispatchEvent(Event.OnSwitchAccountTo(it)) }
            ).launchIn(viewModelScope)

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
        /**
         * Staff-only rows need beta access (staff, or the version-footer override); flag-gated rows
         * additionally need their flag switched on server-side.
         */
        private fun buildItemList(
            unlocked: Boolean,
            flags: List<BetaFeature>,
        ): List<MenuItem<Event>> = FullMenuList
            .filter { it !is StaffMenuItem || unlocked }
            .filter { item ->
                val flag = item.featureFlag ?: return@filter true
                flags.find { it.flag.key == flag.key }?.enabled == true
            }

        private val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        isBetaEnabled = event.unlocked,
                        flags = event.flags,
                        items = buildItemList(unlocked = event.unlocked, flags = event.flags),
                    )
                }

                is Event.OpenScreen,
                Event.OnSwitchAccountsClicked,
                is Event.OnSwitchAccountTo,
                Event.OpenBillPlayground,
                Event.OnAccessKeyClicked,
                Event.OnViewAccessKey,
                Event.OnLogOutClicked,
                Event.OnLoggedOutCompletely,
                Event.OnDeleteAccountClicked,
                Event.OnAccountDeleted -> { state -> state }
            }
        }
    }
}