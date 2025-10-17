package com.flipcash.app.tokens

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class TokenInfoViewModel @Inject constructor(
    private val tokenController: TokenController,
    private val exchange: Exchange,
    private val shareController: ShareSheetController,
): BaseViewModel2<TokenInfoViewModel.State, TokenInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val token: Token? = null,
        val marketCap: Fiat? = null,
        val balance: LocalFiat = LocalFiat.Zero,
        val descriptionExpanded: Boolean = false,
    )

    sealed interface Event {
        data class OnTokenChanged(val token: Token): Event
        data class OnMarketCapChanged(val mcap: Fiat?): Event
        data class OnBalanceUpdated(val balance: LocalFiat): Event
        data class ExpandDescription(val expand: Boolean): Event
        data object Share: Event
        data class OpenScreen(val screen: AppRoute): Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnTokenChanged>()
            .map { it.token }
            .distinctUntilChanged()
            .flatMapLatest {
                combine(
                tokenController.balanceForToken(it.address),
                    exchange.observeBalanceRate(),
                ) { balance, rate ->
                    LocalFiat(
                        usdc = balance,
                        nativeAmount = balance.convertingTo(rate),
                    )
                }
            }.onEach {
                dispatchEvent(Event.OnBalanceUpdated(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBalanceUpdated>()
            .map { it.balance }
            .map { balance ->
                val token = stateFlow.value.token ?: return@map null
                token.marketCap()
            }
            .flatMapLatest { mcap ->
                combine(
                    flowOf(mcap),
                    exchange.observeBalanceRate(),
                ) { usdMcap, rate ->
                    usdMcap?.convertingTo(rate)
                }
            }.onEach { dispatchEvent(Event.OnMarketCapChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Share>()
            .mapNotNull { stateFlow.value.token }
            .map { Shareable.TokenInfo(it) }
            .onEach { shareController.present(it) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnTokenChanged -> { state -> state.copy(token = event.token) }
                is Event.OnMarketCapChanged -> { state -> state.copy(marketCap = event.mcap) }
                is Event.OnBalanceUpdated -> { state -> state.copy(balance = event.balance) }
                is Event.ExpandDescription -> { state -> state.copy(descriptionExpanded = event.expand) }
                is Event.OpenScreen -> { state -> state }
                is Event.Share -> { state -> state }
            }
        }
    }
}