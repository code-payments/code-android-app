package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.bondingcurve.BondingCurveProjection
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

@HiltViewModel
class MarketCapExplainerViewModel @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel<MarketCapExplainerViewModel.State, MarketCapExplainerViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val token: Token? = null,
        /** Null until the token is cached and its supply is known. */
        val projection: BondingCurveProjection? = null,
        /** The pill's appreciation in the user's currency; null for reserves or before it loads. */
        val appreciation: Fiat? = null,
        /** The user's preferred in-app currency rate, the one `TokenInfoViewModel` converts with. */
        val rate: Rate = Rate.oneToOne,
    )

    sealed interface Event {
        data class OnMintProvided(val mint: Mint) : Event
        data class OnLoaded(val token: Token, val projection: BondingCurveProjection) : Event
        data class OnAppreciationUpdated(val appreciation: Fiat?) : Event
        data class OnRateUpdated(val rate: Rate) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnMintProvided>()
            .map { it.mint }
            .distinctUntilChanged()
            .flatMapLatest { mint ->
                combine(
                    tokenCoordinator.observeTokenCache().map { it[mint] }.onStart { tokenCoordinator.getTokenMetadata(mint) },
                    tokenCoordinator.heldQuarksForToken(mint),
                    tokenCoordinator.balanceForToken(mint),
                ) { token, heldQuarks, balance ->
                    // No quarks is "unknown" for a balance restored from Room, but a zero balance
                    // means the user holds none, which is a known 0.
                    val quarks = heldQuarks ?: if (balance.quarks == 0L) 0L else null
                    val supply = token?.launchpadMetadata?.currentCirculatingSupplyQuarks
                    if (token == null || supply == null) return@combine null
                    Event.OnLoaded(
                        token = token,
                        projection = BondingCurveProjection(token, supply, quarks),
                    )
                }
            }
            .flowOn(dispatchers.Default)
            .onEach { it?.let(::dispatchEvent) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnMintProvided>()
            .flatMapLatest { event ->
                combine(
                    tokenCoordinator.appreciationForToken(event.mint),
                    exchange.observePreferredRate(),
                ) { appreciation, rate ->
                    // USD reserves track "no appreciation" as MIN_VALUE.
                    if (appreciation == Fiat.MIN_VALUE) null else appreciation.convertingTo(rate)
                }
            }
            .onEach { dispatchEvent(Event.OnAppreciationUpdated(it)) }
            .launchIn(viewModelScope)

        exchange.observePreferredRate()
            .onEach { dispatchEvent(Event.OnRateUpdated(it)) }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.OnMintProvided -> { state -> state }
                is Event.OnLoaded -> { state ->
                    state.copy(token = event.token, projection = event.projection)
                }
                is Event.OnAppreciationUpdated -> { state -> state.copy(appreciation = event.appreciation) }
                is Event.OnRateUpdated -> { state -> state.copy(rate = event.rate) }
            }
        }
    }
}
