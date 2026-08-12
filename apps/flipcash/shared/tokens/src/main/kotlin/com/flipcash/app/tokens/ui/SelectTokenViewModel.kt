package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.shared.tokens.R
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
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

                // Sum the UNROUNDED per-token values, letting display formatting round the total —
                // i.e. round(sum(x)), not sum(round(x)). Rounding each token to cents first drifts the
                // total by up to a penny and diverged from iOS (ExchangedFiat.total sums unrounded) and
                // from our own aggregateAppreciation (which already sums unrounded).
                return set.map { it.balance }.sum()
            }

        val aggregateAppreciation: LocalFiat?
            get() = tokens?.map { it.appreciation }?.sum()
    }

    sealed interface Event {
        data class OnRateChanged(val rate: Rate) : Event

        data class OnPurposeChanged(val purpose: TokenPurpose) : Event
        data class OnTokensUpdated(val tokens: List<TokenWithLocalizedBalance>) : Event

        data class OnTokenSelected(val mint: Mint, val fromUser: Boolean = true) : Event

        data object OnTokenChanged : Event

        data class OpenScreen(val route: AppRoute) : Event

        data class OnCanGiveUsdf(val enabled: Boolean) : Event
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
                    val rateForPurpose = when (purpose) {
                        is TokenPurpose.LaunchFunding -> exchange.rateForUsd()
                        else -> rate
                    }
                    balances
                        .map {
                            val balance = LocalFiat(
                                usdf = it.balance,
                                nativeAmount = it.balance.convertingTo(rateForPurpose),
                            )

                            // USD reserves don't appreciate so we track that as MIN_VALUE internally to avoid confusion
                            // with true zero's.
                            val appreciation = if (it.appreciation == Fiat.MIN_VALUE) {
                                LocalFiat(
                                    usdf = 0.toFiat(),
                                    nativeAmount = 0.toFiat(rateForPurpose.currency),
                                )
                            } else {
                                LocalFiat(
                                    usdf = it.appreciation,
                                    nativeAmount = it.appreciation.convertingTo(rateForPurpose),
                                )
                            }

                            TokenWithLocalizedBalance(
                                token = it.token,
                                balance = balance,
                                appreciation = appreciation,
                                displayName = when (purpose) {
                                    TokenPurpose.Balance -> {
                                        if (it.token.address == Mint.usdf && featureFlags.get(FeatureFlag.NewUi)) {
                                            resources.getString(R.string.displayName_dollars)
                                        } else {
                                            it.token.name
                                        }
                                    }

                                    is TokenPurpose.Swap,
                                    is TokenPurpose.LaunchFunding,
                                    is TokenPurpose.Tip,
                                    TokenPurpose.Deposit,
                                    TokenPurpose.Withdraw -> {
                                        if (it.token.address == Mint.usdf) {
                                            if (featureFlags.get(FeatureFlag.NewUi)) {
                                                resources.getString(R.string.displayName_dollars)
                                            } else {
                                                resources.getString(R.string.displayName_usdf)
                                            }
                                        } else {
                                            it.token.name
                                        }
                                    }

                                    is TokenPurpose.Select -> it.token.name

                                }
                            )
                        }
                        .sortedWith(
                            // Match iOS (Session.balances): descending by exact value, then
                            // alphabetical by name (not by mint address, which reordered equal-value
                            // tokens like LaunchIt/Teddies differently than iOS).
                            compareByDescending<TokenWithLocalizedBalance> { it.balance.nativeAmount }
                                .thenBy { it.token.name }
                        )
                        .filter {
                            val hasBalance = it.balance.nativeAmount.hasDisplayableValue
                            when (purpose) {
                                // show all tokens we have accounts for as deposit targets
                                TokenPurpose.Deposit -> true

                                is TokenPurpose.Select -> {
                                    if (it.token.address == Mint.usdf) {
                                        stateFlow.value.canGiveUsdf && hasBalance
                                    } else {
                                        hasBalance
                                    }
                                }

                                is TokenPurpose.LaunchFunding -> {
                                    hasBalance
                                }

                                is TokenPurpose.Tip -> {
                                    hasBalance
                                }

                                is TokenPurpose.Swap -> {
                                    if (it.token.address != purpose.desiredToken) {
                                        hasBalance
                                    } else {
                                        false
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
            .filter { stateFlow.value.purpose is TokenPurpose.TriggersChange }
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