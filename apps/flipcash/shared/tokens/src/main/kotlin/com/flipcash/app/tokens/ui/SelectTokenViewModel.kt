package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.TokenSyncState
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
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
    dispatchers: DispatcherProvider,
) : BaseViewModel<SelectTokenViewModel.State, SelectTokenViewModel.Event>(
    initialState = State(purpose = TokenPurpose.Balance),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    data class State(
        val purpose: TokenPurpose,
        val rate: Rate = Rate.oneToOne,
        val discoveryEnabled: Boolean = false,
        val tokens: List<TokenWithLocalizedBalance>? = null,
        val selectedToken: Mint? = null,
        val syncState: TokenSyncState = TokenSyncState.Unknown,
    ) {
        /**
         * Whether the token set is still settling.
         *
         * Null tokens means persistence hasn't reported yet. An *empty* set is the ambiguous case:
         * the token cache starts empty on a fresh login, so until a fetch has actually completed
         * (see [TokenSyncState]) "no tokens" is indistinguishable from "not looked yet". Callers that
         * render token metadata for data sourced elsewhere — the wallet's recent-activity preview,
         * whose convert rows read "USDF -> Dad Cash" only once both mints resolve — must wait it out
         * rather than draw the unresolved fallback ("Converted"). A non-empty set short-circuits the
         * wait: there is already token metadata to resolve against.
         */
        val isAwaitingTokens: Boolean
            get() {
                val set = tokens ?: return true
                return set.isEmpty() && syncState == TokenSyncState.Unknown
            }

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


        data class OnSyncStateChanged(val syncState: TokenSyncState) : Event
    }

    init {
        exchange.observePreferredRate()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRateChanged(it)) }
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
                                        it.token.name
                                    }

                                    is TokenPurpose.Swap,
                                    is TokenPurpose.ConvertDestination,
                                    is TokenPurpose.BuyFunding,
                                    is TokenPurpose.LaunchFunding,
                                    is TokenPurpose.Tip,
                                    TokenPurpose.Deposit,
                                    TokenPurpose.Withdraw -> {
                                        it.token.name
                                    }

                                    is TokenPurpose.Select -> it.token.name

                                }
                            )
                        }
                        .sortedWith(BalanceOrder)
                        .filter {
                            val hasBalance = it.balance.nativeAmount.hasDisplayableValue
                            when (purpose) {
                                // show all tokens we have accounts for as deposit targets
                                TokenPurpose.Deposit -> true

                                is TokenPurpose.Select -> hasBalance

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

                                // A conversion moves between currencies the user already holds;
                                // acquiring something new is a Get, not a Convert.
                                is TokenPurpose.ConvertDestination -> {
                                    it.token.address != purpose.source && hasBalance
                                }

                                // Anything held except the currency being bought can fund a Get.
                                is TokenPurpose.BuyFunding -> {
                                    it.token.address != purpose.target && hasBalance
                                }
                                // show all tokens with non-zero balance
                                else -> hasBalance
                            }
                        }
                }
            }.onEach { dispatchEvent(Event.OnTokensUpdated(it)) }
            .launchIn(viewModelScope)

        // Whether an empty token set means "holds nothing" or "we haven't looked yet"
        // (see State.isAwaitingTokens).
        tokenCoordinator.syncState
            .onEach { dispatchEvent(Event.OnSyncStateChanged(it)) }
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
        /**
         * Descending by displayed value, then alphabetical by name (not by mint address, which
         * reordered equal-value tokens like LaunchIt/Teddies differently than iOS).
         *
         * Compared at display precision rather than on the raw [Fiat], which carries six decimal
         * places against the two USD shows. Two cards both reading $1.00 differ in digits nobody can
         * see, and a launchpad currency's value moves in those digits on every price refresh — so an
         * exact comparison had Dollars and Dad Cash trading places under the user while the deck was
         * on screen. Rounding first means cards showing the same figure hold a stable order.
         *
         * iOS (Session.balances) still sorts on the exact value and has the same swap latent in it.
         */
        val BalanceOrder: Comparator<TokenWithLocalizedBalance> =
            compareByDescending<TokenWithLocalizedBalance> { it.balance.nativeAmount.toDouble() }
                .thenBy { it.token.name }

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
                is Event.OnSyncStateChanged -> { state -> state.copy(syncState = event.syncState) }
            }
        }
    }
}