package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.util.abbreviated
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.bondingcurve.BondingCurveProjection
import com.flipcash.app.tokens.bondingcurve.ExplainerTick
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
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
        /** What the screen shows that does not move with the slider; null until [projection] loads. */
        val labels: Labels? = null,
        /** The token could not be fetched or has no bonding supply, so there is nothing to project. */
        val unavailable: Boolean = false,
        /**
         * Whether the user holds any of the token. A balance known only in USD still counts; a known
         * zero does not, and the screen then drops the worth readout and the ownership card, as iOS does.
         */
        val holdsToken: Boolean = false,
    ) {
        val isLoading: Boolean get() = projection == null && !unavailable
    }

    /** Display strings in the preferred currency. A null field is unknown and reads as a dash. */
    data class Labels(
        val ticks: Map<ExplainerTick, String>,
        val tokensHeld: String?,
        val price: String,
        val shareOfCirculating: String?,
        val shareOfMax: String?,
    )

    sealed interface Event {
        data class OnMintProvided(val mint: Mint) : Event
        data class OnLoaded(
            val token: Token,
            val projection: BondingCurveProjection,
            val holdsToken: Boolean,
        ) : Event
        data object OnUnavailable : Event
        data class OnAppreciationUpdated(val appreciation: Fiat?) : Event
        data class OnRateUpdated(val rate: Rate) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnMintProvided>()
            .map { it.mint }
            .distinctUntilChanged()
            .flatMapLatest { mint ->
                // The coordinator caches a network token only when the user has an account for it,
                // so a token opened from Discovery never reaches the cache; fall back to the fetch.
                // Null while the fetch is in flight.
                val fetched = flow<Result<Token>?> { emit(tokenCoordinator.getTokenMetadata(mint).map { it.token }) }
                    .onStart { emit(null) }
                combine(
                    tokenCoordinator.observeTokenCache().map { it[mint] },
                    fetched,
                    tokenCoordinator.heldQuarksForToken(mint),
                    tokenCoordinator.balanceForToken(mint),
                ) { cached, fetchResult, heldQuarks, balance ->
                    val token = cached ?: fetchResult?.getOrNull()
                    if (token == null) {
                        return@combine if (fetchResult == null) null else Event.OnUnavailable
                    }
                    // No quarks is "unknown" for a balance restored from Room, but a zero balance
                    // means the user holds none, which is a known 0.
                    val quarks = heldQuarks ?: if (balance.quarks == 0L) 0L else null
                    val supply = token.launchpadMetadata?.currentCirculatingSupplyQuarks
                        ?: return@combine Event.OnUnavailable
                    Event.OnLoaded(
                        token = token,
                        projection = BondingCurveProjection(token, supply, quarks),
                        holdsToken = quarks == null || quarks > 0,
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
                    state.copy(
                        token = event.token,
                        projection = event.projection,
                        labels = labels(event.projection, state.rate),
                        unavailable = false,
                        holdsToken = event.holdsToken,
                    )
                }
                Event.OnUnavailable -> { state -> state.copy(unavailable = true) }
                is Event.OnAppreciationUpdated -> { state -> state.copy(appreciation = event.appreciation) }
                is Event.OnRateUpdated -> { state ->
                    state.copy(rate = event.rate, labels = state.projection?.let { labels(it, event.rate) })
                }
            }
        }

        private val MIN_SHOWN_PERCENT = BigDecimal("0.01")

        internal fun labels(projection: BondingCurveProjection, rate: Rate): Labels {
            val ownership = projection.ownership()
            return Labels(
                ticks = projection.ticks.associateWith { Fiat(it.reserve.toDouble()).convertingTo(rate).abbreviated() },
                tokensHeld = ownership.tokensHeld?.let {
                    NumberFormat.getIntegerInstance().format(it.setScale(0, RoundingMode.DOWN))
                },
                price = price(ownership.price, rate),
                shareOfCirculating = ownership.shareOfCirculating?.let(::percent),
                shareOfMax = ownership.shareOfMax?.let(::percent),
            )
        }

        /** A USD per-token price in [rate]'s currency, to four significant figures: `$0.02994`, `$8.78`. */
        internal fun price(usd: BigDecimal, rate: Rate): String {
            val converted = BigDecimal(usd.toDouble() * rate.fx)
            var rounded = converted.round(MathContext(4, RoundingMode.HALF_UP)).stripTrailingZeros()
            val minDigits = rate.currency.fractionDigits
            if (rounded.scale() < minDigits) rounded = rounded.setScale(minDigits)
            return Fiat(fiat = rounded.toDouble(), currencyCode = rate.currency)
                .formatted(rule = Fiat.FormattingRule.Length(rounded.scale()))
        }

        /** A share as a percentage to two decimals, or `<0.01%` for a positive share below that. */
        internal fun percent(value: BigDecimal): String {
            if (value.signum() == 0) return "0%"
            if (value.signum() > 0 && value < MIN_SHOWN_PERCENT) return "<0.01%"
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
        }
    }
}
