package com.flipcash.app.tipping.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.exchange.Exchange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the New Chat chooser can open the currency-gated form, by the same [communityCurrencies]
 * the form defaults from.
 */
@HiltViewModel
internal class NewChatViewModel @Inject constructor(
    tokenCoordinator: TokenCoordinator,
    exchange: Exchange,
) : ViewModel() {

    /**
     * Null until both balances and a rate have arrived. The chooser opens the form while it is
     * unknown rather than turning the user away on a guess; the form keeps Create disabled until a
     * currency is seated.
     */
    val hasCommunityCurrency: StateFlow<Boolean?> =
        combine(tokenCoordinator.tokenBalances, exchange.observePreferredRate()) { balances, rate ->
            communityCurrencies(balances, rate).isNotEmpty()
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
