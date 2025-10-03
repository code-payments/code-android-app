package com.flipcash.app.balance.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
internal class BalanceViewModel @Inject constructor(
    tokenController: TokenController,
    userManager: UserManager,
    exchange: Exchange,
    onrampController: OnRampAmountController,
) : BaseViewModel2<BalanceViewModel.State, BalanceViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val balances: List<TokenWithLocalizedBalance>? = null,
        val preferredOnRampProvider: OnRampProvider? = null,
    ) {
        val totalBalance: LocalFiat?
            get() = balances.orEmpty().map { it.balance }.sum()
    }

    sealed interface Event {
        data class OnBalancesUpdated(val balances: List<TokenWithLocalizedBalance>) : Event
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider?) : Event

        data object OpenCurrencySelection : Event

        data object OnAddCashClicked : Event
        data object OpenOnRampAmountModal : Event
        data object OnWithdrawClicked : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    init {
        combine(
            tokenController.tokenBalances,
            exchange.observeBalanceRate(),
        ) { balances, rate ->
            balances.map {
                TokenWithLocalizedBalance(
                    token = it.token,
                    balance = LocalFiat(
                        usdc = it.balance,
                        converted = it.balance.convertingTo(rate),
                        rate = rate

                    )
                )
            }.sortedByDescending { it.balance.converted }
        }.onEach {
            dispatchEvent(Event.OnBalancesUpdated(it))
        }.launchIn(viewModelScope)

        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.preferredOnRampProvider }
            .onEach { provider ->
                dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider))
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAddCashClicked>()
            .onEach {
                val provider = stateFlow.value.preferredOnRampProvider
                if (provider is OnRampProvider.Coinbase && provider.type == OnRampType.Virtual) {
                    // has coinbase provider supporting google pay - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal)
                } else {
                    // route to provider list
                    dispatchEvent(Event.OpenScreen(AppRoute.OnRamp.ProviderList(AppRoute.Sheets.Wallet)))
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
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OpenCurrencySelection -> { state -> state }
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }

                Event.OnAddCashClicked -> { state -> state }
                Event.OpenOnRampAmountModal -> { state -> state }
                Event.OnWithdrawClicked -> { state -> state }
                is Event.OnBalancesUpdated -> { state -> state.copy(balances = event.balances) }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}