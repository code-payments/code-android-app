package com.flipcash.app.tokens

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.analytics.AnalyticsEvent
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SelectTokenViewModel @Inject constructor(
    userManager: UserManager,
    tokenController: TokenController,
    exchange: Exchange,
    analytics: FlipcashAnalyticsService,
    featureFlags: FeatureFlagController,
    resources: ResourceHelper,
) : BaseViewModel2<SelectTokenViewModel.State, SelectTokenViewModel.Event>(
    initialState = State(purpose = TokenPurpose.Balance),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val purpose: TokenPurpose,
        val reservesEnabled: Boolean = false,
        val preferredOnRampProvider: OnRampProvider? = null,
        val tokens: List<TokenWithLocalizedBalance>? = null,
        val selectedToken: Mint? = null,
    ) {
        val totalBalance: LocalFiat
            get() = tokens.orEmpty().map { it.balance }.sum()

        val aggregateAppreciation: LocalFiat?
            get() = tokens?.map { it.appreciation }?.sum()
    }

    sealed interface Event {
        data class OnPreferredOnRampProviderChanged(val provider: OnRampProvider?) : Event
        data class OnReservesEnabled(val enabled: Boolean) : Event

        data class OnPurposeChanged(val purpose: TokenPurpose) : Event
        data class OnTokensUpdated(val tokens: List<TokenWithLocalizedBalance>) : Event

        data class OnTokenSelected(val mint: Mint, val fromUser: Boolean = true) : Event

        data object OnTokenChanged : Event

        data object OnAddCashClicked : Event
        data object OpenOnRampAmountModal : Event
        data class OpenScreen(val route: AppRoute) : Event
    }

    init {
        userManager.state
            .filter { it.authState is AuthState.LoggedInWithUser }
            .mapNotNull { it.flags }
            .map { it.preferredOnRampProvider }
            .onEach { provider ->
                dispatchEvent(Event.OnPreferredOnRampProviderChanged(provider))
            }
            .launchIn(viewModelScope)

        featureFlags.observe(FeatureFlag.CashReservesEnabled)
            .onEach { dispatchEvent(Event.OnReservesEnabled(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                combine(
                    stateFlow,
                    tokenController.tokenBalances,
                    when (purpose) {
                        TokenPurpose.Balance -> exchange.observeBalanceRate()
                        TokenPurpose.Select -> exchange.observeEntryRate()
                        TokenPurpose.Withdraw -> flowOf(exchange.rateForUsd())
                        TokenPurpose.Deposit -> exchange.observeEntryRate()
                    }
                ) { state, balances, rate ->
                    balances
                        .map {
                            TokenWithLocalizedBalance(
                                token = it.token,
                                displayName = if (it.token.address == Mint.usdf) {
                                    resources.getString(R.string.title_cashReserves)
                                } else {
                                    it.token.name
                                },
                                balance = LocalFiat(
                                    usdf = it.balance,
                                    nativeAmount = it.balance.convertingTo(rate),
                                ),
                                // USD reserves don't appreciate so we track that as MIN_VALUE internally to avoid confusion
                                // with true zero's.
                                appreciation = if (it.appreciation == Fiat.MIN_VALUE) {
                                    LocalFiat(
                                        usdf = 0.toFiat(),
                                        nativeAmount = 0.toFiat(rate.currency),
                                    )
                                } else {
                                    LocalFiat(
                                        usdf = it.appreciation,
                                        nativeAmount = it.appreciation.convertingTo(rate),
                                    )
                                }
                            )
                        }
                        .sortedWith(compareByDescending { item ->
                            if (state.reservesEnabled && item.isReserves) Fiat.MIN_VALUE
                            else item.balance.nativeAmount
                        })
                        .filter {
                            val baselineAmount = 0.01.toFiat(rate.currency)
                            when (purpose) {
                                // show all tokens we have accounts for as deposit targets
                                TokenPurpose.Deposit -> true
                                // when reserves are enabled, we must prevent sending USDF directly (it's used for reserves)
                                TokenPurpose.Select -> !state.reservesEnabled || it.token.address != Mint.usdf
                                // show all tokens with non-zero balance
                                else -> {
                                    it.balance.nativeAmount.valueNonZero() &&
                                            it.balance.nativeAmount.valueGreaterThanOrEqualTo(baselineAmount)
                                }
                            }
                        }
                }
            }.onEach { dispatchEvent(Event.OnTokensUpdated(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAddCashClicked>()
            .onEach {
                analytics.openOnramp(AnalyticsEvent.OnRampOpenEvent.Balance)
                val provider = stateFlow.value.preferredOnRampProvider
                if (provider is OnRampProvider.Coinbase && provider.type == OnRampType.Virtual) {
                    // has coinbase provider supporting google pay - pop selection for quick add
                    dispatchEvent(Event.OpenOnRampAmountModal)
                }
            }.launchIn(viewModelScope)

        tokenController.observeSelectedTokenMint()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnTokenSelected(it, fromUser = false)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnTokenSelected>()
            .filter { stateFlow.value.purpose is TokenPurpose.Select }
            .filter { it.fromUser }
            .map { it.mint }
            .onEach { tokenController.selectToken(it) }
            .onEach { dispatchEvent(Event.OnTokenChanged) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPreferredOnRampProviderChanged -> { state ->
                    state.copy(preferredOnRampProvider = event.provider)
                }

                is Event.OnReservesEnabled -> { state ->
                    state.copy(reservesEnabled = event.enabled)
                }

                is Event.OnPurposeChanged -> { state -> state.copy(purpose = event.purpose) }
                is Event.OnTokensUpdated -> { state -> state.copy(tokens = event.tokens) }
                is Event.OnTokenSelected -> { state -> state.copy(selectedToken = event.mint) }
                is Event.OnTokenChanged -> { state -> state }
                is Event.OnAddCashClicked -> { state -> state }
                is Event.OpenOnRampAmountModal -> { state -> state }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}