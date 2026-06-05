package com.flipcash.app.myaccount.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.menu.StaffMenuItem
import com.flipcash.features.myaccount.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
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
    add(UserProfile)
    add(LogOut)
    add(DeleteAccount)
}

@HiltViewModel
internal class MyAccountScreenViewModel @Inject constructor(
    userManager: UserManager,
    featureFlagController: FeatureFlagController,
    resources: ResourceHelper,
    authManager: AuthManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel<MyAccountScreenViewModel.State, MyAccountScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val isBetaEnabled: Boolean = false,
        val items: List<MenuItem<Event>> = FullMenuList
    )

    internal sealed interface Event {
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data object OnAccessKeyClicked : Event
        data object OnViewAccessKey : Event
        data object OnContactMethodsClicked : Event
        data object OnViewUserProfile : Event
        data object OnDeleteAccountClicked : Event
        data object OnAccountDeleted : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
    }

    init {
        combine(
            featureFlagController.observeOverride(),
            userManager.state.map { it.flags?.isStaff == true }
        ) { override, isStaff ->
            override || isStaff
        }.map {
            dispatchEvent(Event.OnBetaFeaturesUnlocked(it))
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
            .filterIsInstance<Event.OnContactMethodsClicked>()
            .onEach {
                dispatchEvent(Event.OnViewUserProfile)
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
                                    .onSuccess {
                                        dispatchEvent(Event.OnLoggedOutCompletely)
                                    }
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
    }

    internal companion object {
        private fun buildItemList(
            isBetaEnabled: Boolean,
        ): List<MenuItem<Event>> {
            return if (isBetaEnabled) {
                FullMenuList
            } else {
                FullMenuList.filterNot { item -> item is StaffMenuItem }
            }
        }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnLogOutClicked,
                Event.OnLoggedOutCompletely,
                Event.OnContactMethodsClicked,
                Event.OnViewUserProfile,
                Event.OnViewAccessKey,
                Event.OnDeleteAccountClicked,
                Event.OnAccountDeleted,
                Event.OnAccessKeyClicked -> { state -> state }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        isBetaEnabled = event.unlocked,
                        items = buildItemList(event.unlocked)
                    )
                }
            }
        }
    }
}