package com.flipcash.app.tokens

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.tokens.TokenPurpose
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SelectTokenViewModel @Inject constructor(
    tokenController: TokenController,
    exchange: Exchange,
): BaseViewModel2<SelectTokenViewModel.State, SelectTokenViewModel.Event>(
    initialState = State(purpose = TokenPurpose.Balance),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val purpose: TokenPurpose,
        val tokens: List<TokenWithLocalizedBalance>? = null,
    ) {
        val totalBalance: LocalFiat?
            get() = tokens.orEmpty().map { it.balance }.sum()
    }

    sealed interface Event {
        data class OnPurposeChanged(val purpose: TokenPurpose) : Event
        data class OnTokensUpdated(val tokens: List<TokenWithLocalizedBalance>) : Event

        data class OnTokenSelected(val token: Token): Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                combine(
                    tokenController.tokenBalances,
                    when (purpose) {
                        TokenPurpose.Balance -> exchange.observeBalanceRate()
                        TokenPurpose.Send -> exchange.observeEntryRate()
                        TokenPurpose.Withdraw -> exchange.observeEntryRate()
                    }
                ) { balances, rate ->
                    balances.map {
                        TokenWithLocalizedBalance(
                            token = it.token,
                            balance = LocalFiat(
                                usdc = it.balance,
                                nativeAmount = it.balance.convertingTo(rate),
                            )
                        )
                    }.sortedByDescending { it.balance.nativeAmount }.filter { it.balance.nativeAmount > Fiat.Zero }
                }
            }.onEach { dispatchEvent(Event.OnTokensUpdated(it)) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPurposeChanged -> { state -> state.copy(purpose = event.purpose) }
                is Event.OnTokensUpdated -> { state -> state.copy(tokens = event.tokens) }
                is Event.OnTokenSelected -> { state -> state }
            }
        }
    }
}