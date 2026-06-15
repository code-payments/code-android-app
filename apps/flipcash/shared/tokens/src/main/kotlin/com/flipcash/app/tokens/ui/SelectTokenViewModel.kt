package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.shared.tokens.R
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.rounded
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SelectTokenViewModel @Inject constructor(
    tokenCoordinator: TokenCoordinator,
    exchange: Exchange,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    featureFlags: FeatureFlagController,
) : BaseViewModel<SelectTokenViewModel.State, SelectTokenViewModel.Event>(
    initialState = State(purpose = TokenPurpose.Balance),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val purpose: TokenPurpose,
        val rate: Rate = Rate.oneToOne,
        val canGiveUsdf: Boolean = false,
        val discoveryEnabled: Boolean = false,
        val tokens: List<TokenWithLocalizedBalance>? = null,
        val selectedToken: Mint? = null,
    ) {
        val totalBalance: LocalFiat?
            get() {
                val set = tokens ?: return null
                if (set.isEmpty()) {
                    return LocalFiat.Zero
                        .copy(
                            nativeAmount = 0.toFiat(currencyCode = rate.currency),
                            rate = rate
                        )
                }

                return set.map { it.balance.rounded() }.sum()
            }

        val aggregateAppreciation: LocalFiat?
            get() = tokens?.map { it.appreciation }?.sum()
    }

    sealed interface Event {
        data class OnRateChanged(val rate: Rate): Event

        data class OnPurposeChanged(val purpose: TokenPurpose) : Event
        data class OnTokensUpdated(val tokens: List<TokenWithLocalizedBalance>) : Event

        data class OnTokenSelected(val mint: Mint, val fromUser: Boolean = true) : Event

        data object OnTokenChanged : Event

        data class OpenScreen(val route: AppRoute) : Event

        data class OnCanGiveUsdf(val enabled: Boolean): Event
    }

    init {
        exchange.observePreferredRate()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRateChanged(it)) }
            .launchIn(viewModelScope)

        featureFlags.observe(FeatureFlag.GiveUsdf)
            .onEach { dispatchEvent(Event.OnCanGiveUsdf(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                combine(
                    tokenCoordinator.tokenBalances,
                    exchange.observePreferredRate()
                ) { balances, rate ->
                    balances
                        .map {
                            val balance = LocalFiat(
                                usdf = it.balance,
                                nativeAmount = it.balance.convertingTo(rate),
                            )

                            // USD reserves don't appreciate so we track that as MIN_VALUE internally to avoid confusion
                            // with true zero's.
                            val appreciation = if (it.appreciation == Fiat.MIN_VALUE) {
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

                            TokenWithLocalizedBalance(
                                token = it.token,
                                balance = balance,
                                appreciation = appreciation,
                                displayName = when (purpose) {
                                    TokenPurpose.Balance -> it.token.name
                                    TokenPurpose.Deposit -> {
                                        if (it.token.address == Mint.usdf) {
                                            resources.getString(R.string.displayName_usdf)
                                        } else {
                                            it.token.name
                                        }
                                    }

                                    TokenPurpose.Select -> it.token.name
                                    TokenPurpose.Withdraw -> {
                                        if (it.token.address == Mint.usdf) {
                                            resources.getString(R.string.displayName_usdf)
                                        } else {
                                            it.token.name
                                        }
                                    }
                                }
                            )
                        }
                        .sortedWith(
                            compareByDescending<TokenWithLocalizedBalance> { it.balance.nativeAmount.rounded() }
                                .thenBy { it.token.address.base58() }
                        )
                        .filter {
                            val hasBalance = it.balance.nativeAmount.hasDisplayableValue
                            when (purpose) {
                                // show all tokens we have accounts for as deposit targets
                                TokenPurpose.Deposit -> true
                                TokenPurpose.Select -> {
                                    if (it.token.address == Mint.usdf) {
                                        stateFlow.value.canGiveUsdf && hasBalance
                                    } else {
                                        hasBalance
                                    }
                                }
                                // show all tokens with non-zero balance
                                else -> hasBalance
                            }
                        }
                }
            }.onEach { dispatchEvent(Event.OnTokensUpdated(it)) }
            .launchIn(viewModelScope)

        tokenCoordinator.observeSelectedTokenMint()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnTokenSelected(it, fromUser = false)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnTokenSelected>()
            .filter { stateFlow.value.purpose is TokenPurpose.Select }
            .filter { it.fromUser }
            .map { it.mint }
            .onEach { tokenCoordinator.selectToken(it) }
            .onEach { dispatchEvent(Event.OnTokenChanged) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnRateChanged -> { state ->
                    state.copy(rate = event.rate)
                }

                is Event.OnPurposeChanged -> { state -> state.copy(purpose = event.purpose) }
                is Event.OnTokensUpdated -> { state -> state.copy(tokens = event.tokens) }
                is Event.OnTokenSelected -> { state -> state.copy(selectedToken = event.mint) }
                is Event.OnTokenChanged -> { state -> state }
                is Event.OpenScreen -> { state -> state }
                is Event.OnCanGiveUsdf -> { state -> state.copy(canGiveUsdf = event.enabled) }
            }
        }
    }
}