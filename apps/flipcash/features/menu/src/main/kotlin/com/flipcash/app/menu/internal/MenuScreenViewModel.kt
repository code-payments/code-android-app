package com.flipcash.app.menu.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.features.menu.BuildConfig
import com.flipcash.features.menu.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private val DefaultMenuItems = buildList {
//    add(Deposit)
//    add(Withdraw)
//    add(MyAccount)
//    add(AppSettings)
    add(LogOut)
}

private val StaffMenuItems = buildList {
//    add(Deposit)
//    add(Withdraw)
//    add(MyAccount)
//    add(AppSettings)
//    add(SwitchAccount)
//    add(Labs)
    add(LogOut)
}

@HiltViewModel
internal class MenuScreenViewModel @Inject constructor(
    private val resources: ResourceHelper,
    userManager: UserManager,
    authManager: AuthManager,
    versionInfo: VersionInfo,
) :
    BaseViewModel2<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent
    ) {
    data class State(
        val items: List<MenuItem> = DefaultMenuItems,
        val appVersionInfo: VersionInfo = VersionInfo(),
        val isLabsUnlocked: Boolean = false,
    )

    sealed interface Event {
        data class OnAppVersionUpdated(val versionInfo: VersionInfo): Event
        data class OnStaffUserDetermined(val staff: Boolean) : Event
        data object OnDepositClicked : Event
        data object OnWithdrawClicked : Event
        data object OnMyAccountClicked : Event
        data object OnAppSettingsClicked : Event
        data object OnSwitchAccountsClicked : Event
        data object OnLabsClicked : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely: Event
    }

    init {
        userManager.state
            .mapNotNull { it.flags }
            .map { it.isStaff }
            .onEach {
                dispatchEvent(Event.OnAppVersionUpdated(versionInfo))
                dispatchEvent(Event.OnStaffUserDetermined(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogOutClicked>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_logout),
                        subtitle = resources.getString(R.string.prompt_description_logout),
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

                                    }
                            }
                        }
                    )
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnAppVersionUpdated -> { state ->
                    state.copy(appVersionInfo = event.versionInfo)
                }

                is Event.OnStaffUserDetermined -> { state ->
                    state.copy(items = if (event.staff) StaffMenuItems else DefaultMenuItems)
                }
                Event.OnDepositClicked,
                Event.OnWithdrawClicked,
                Event.OnMyAccountClicked,
                Event.OnAppSettingsClicked,
                Event.OnLogOutClicked,
                Event.OnSwitchAccountsClicked,
                Event.OnLabsClicked -> { state -> state }

                Event.OnLoggedOutCompletely -> { state -> state }
            }
        }
    }
}