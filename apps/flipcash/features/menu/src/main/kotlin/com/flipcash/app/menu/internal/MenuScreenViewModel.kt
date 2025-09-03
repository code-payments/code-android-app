package com.flipcash.app.menu.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.features.menu.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FullMenuList = buildList {
    add(Deposit)
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
    onrampController: OnRampAmountController,
) :
    BaseViewModel2<MenuScreenViewModel.State, MenuScreenViewModel.Event>(
        initialState = State(),
        updateStateForEvent = updateStateForEvent
    ) {
    data class State(
        val items: List<MenuItem<Event>> = FullMenuList,
        val logoTapCount: Int = 0,
        val isStaff: Boolean = false,
        val onrampProviders: List<OnRampProvider> = emptyList(),
        val flags: List<BetaFeature> = emptyList(),
        val unlockedBetaFeaturesManually: Boolean = false,
        val appVersionInfo: VersionInfo = VersionInfo(),
    )

    sealed interface Event {
        data object OnLogoTapped: Event
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean): Event
        data class OnFeatureFlagsUpdated(val flags: List<BetaFeature>): Event
        data class OnOnRampProvidersChanged(val providers: List<OnRampProvider>): Event
        data class OnAppVersionUpdated(val versionInfo: VersionInfo) : Event
        data class OnStaffUserDetermined(val staff: Boolean) : Event
        data class OpenScreen(val screen: AppRoute) : Event
        data object OnSwitchAccountsClicked : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
        data class OnSwitchAccountTo(val entropy: String): Event
        data object OnAddCashClicked: Event
        data object OpenOnRampAmountModal: Event
        data object OnWithdrawClicked: Event
    }

    init {
        dispatchEvent(Event.OnAppVersionUpdated(versionInfo))
        dispatchEvent(Event.OnStaffUserDetermined(false))

        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.isStaff }
            .onEach {
                dispatchEvent(Event.OnStaffUserDetermined(it))
            }.launchIn(viewModelScope)

        featureFlags.observeOverride()
            .onEach { dispatchEvent(Event.OnBetaFeaturesUnlocked(it)) }
            .launchIn(viewModelScope)

        featureFlags.observe()
            .onEach { dispatchEvent(Event.OnFeatureFlagsUpdated(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.supportedOnRampProviders }
            .onEach { providers ->
                dispatchEvent(Event.OnOnRampProvidersChanged(providers))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogoTapped>()
            .map { stateFlow.value.logoTapCount }
            .filter { it > TAP_THRESHOLD }
            .filterNot { stateFlow.value.unlockedBetaFeaturesManually }
            .onEach { featureFlags.enableBetaFeatures() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAddCashClicked>()
            .onEach {
                val providers = stateFlow.value.onrampProviders
                if (providers.any { it is OnRampProvider.Coinbase }) {
                    // has coinbase provider - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal)
                } else {
                    // route to provider list
                    dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.ProviderList(AppRoute.Sheets.Menu)))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdrawClicked>()
            .onEach {
                dispatchEvent(
                    Event.OpenScreen(
                        AppRoute.Transfers.Learn(TransferDirection.Outgoing)
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OpenOnRampAmountModal>()
            .map { onrampController.requestAmountSelection(OnRampProvider.Coinbase(OnRampType.Virtual)) }
            .flatMapLatest {
                onrampController.confirmationEvents.take(1)
            }.onEach { event ->
                when (event) {
                    is ConfirmationEvent.OnConfirmationSuccess -> {
                        when (event.amount) {
                            OnRampAmount.Custom -> dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.AmountEntry))
                            is OnRampAmount.Predefined -> Unit
                        }
                    }

                    ConfirmationEvent.Cancelled -> Unit
                }
            }.launchIn(viewModelScope)

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
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToLogOut),
                                            message = resources.getString(R.string.error_description_failedToLogOut),
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

        private fun buildItemList(
            isStaff: Boolean,
            overrode: Boolean,
            flags: List<BetaFeature> = emptyList(),
        ): List<MenuItem<Event>> {
            return if (isStaff || overrode) {
                FullMenuList
                    .filter { item ->
                        val flagForItem = item.featureFlag
                        if (flagForItem != null) {
                            val match = flags.find { it.flag.key == flagForItem.key }
                            match?.enabled == true
                        } else {
                            true
                        }
                    }
            } else {
                FullMenuList.filterNot { it.isStaffOnly }
                    .filter { item ->
                        val flagForItem = item.featureFlag
                        if (flagForItem != null) {
                            val match = flags.find { it.flag.key == flagForItem.key }
                            match?.enabled == true
                        } else {
                            true
                        }
                    }
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
                        items = buildItemList(
                            isStaff = state.isStaff,
                            overrode = event.unlocked,
                            flags = state.flags
                        )
                    )
                }

                is Event.OnAppVersionUpdated -> { state ->
                    state.copy(appVersionInfo = event.versionInfo)
                }

                is Event.OnStaffUserDetermined -> { state ->
                    state.copy(
                        isStaff = event.staff,
                        items = buildItemList(
                            isStaff = event.staff,
                            overrode = state.unlockedBetaFeaturesManually,
                            flags = state.flags,
                        ),
                    )
                }

                Event.OnLogOutClicked,
                Event.OnSwitchAccountsClicked,
                is Event.OpenScreen,
                Event.OnLoggedOutCompletely,
                is Event.OnSwitchAccountTo -> { state -> state }

                Event.OnAddCashClicked -> { state -> state }
                Event.OnWithdrawClicked -> { state -> state }
                Event.OpenOnRampAmountModal -> { state -> state }
                is Event.OnOnRampProvidersChanged -> { state ->
                    state.copy(onrampProviders = event.providers)
                }

                is Event.OnFeatureFlagsUpdated -> { state ->
                    state.copy(
                        items = buildItemList(
                            isStaff = state.isStaff,
                            overrode = state.unlockedBetaFeaturesManually,
                            flags = event.flags
                        ),
                        flags = event.flags,
                    )
                }
            }
        }
    }
}