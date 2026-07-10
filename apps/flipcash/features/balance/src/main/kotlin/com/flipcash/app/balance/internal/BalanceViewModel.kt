package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    featureFlags: FeatureFlagController,
    analytics: FlipcashAnalyticsService,
) : BaseViewModel<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val depositFirstUx: Boolean = false,
        val preferredOnRampProvider: OnRampProvider.Defined? = null,
    )

    sealed interface Event {
        data class DepositFirstUxEnabled(val enabled: Boolean): Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider.Defined?) : Event

        data object OpenCurrencySelection : Event

        data class OpenScreen(val screen: AppRoute) : Event
        data object PresentDepositOptions: Event
    }

    init {
        featureFlags.observe(FeatureFlag.AddMoneyUX)
            .onEach { dispatchEvent(Event.DepositFirstUxEnabled(it)) }
            .launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.Ready }
            .flatMapLatest { userFlags.resolvedFlags }
            .mapNotNull { it.preferredOnRampProvider.effectiveValue }
            .onEach { provider -> dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .map { stateFlow.value.depositFirstUx }
            .mapNotNull { depositFirstUx ->
                if (!depositFirstUx) {
                    dispatchEvent(Event.OpenScreen(AppRoute.Token.Discovery))
                    return@mapNotNull null
                }
                analytics.addMoneyOpened(Analytics.AddMoneySource.Balance)
                purchaseMethodController.presentDepositOptions(popToRoot = true) }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }
                Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
                is Event.DepositFirstUxEnabled -> { state -> state.copy(depositFirstUx = event.enabled) }
            }
        }
    }
}