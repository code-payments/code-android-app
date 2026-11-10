package com.flipcash.app.tipping.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The New Chat chooser: where each of its rows leads. The community row is gated on the account
 * holding a community currency, by the same [communityCurrencies] the form defaults from.
 *
 * Takes the balances and preferred rate as flows rather than their owners, so the Puppet host can
 * run it over a seeded balance; the Hilt entry point passes `TokenCoordinator.tokenBalances` and
 * `Exchange.observePreferredRate()`.
 */
open class NewChatViewModel(
    tokenBalances: Flow<List<TokenWithBalance>>,
    preferredRate: Flow<Rate>,
) : ViewModel() {

    /** A row of the chooser. */
    enum class Choice { FindByUsername, PrivateGroup, CommunityGroup }

    /** What tapping a row does. */
    sealed interface Outcome {
        data class Open(val route: AppRoute) : Outcome

        /** Node 12370:77242's sheet. Its Explore pushes [explore] onto this stack. */
        data object NoCommunityCurrencies : Outcome {
            val explore: AppRoute = AppRoute.Token.Discovery
        }
    }

    /**
     * Null until both balances and a rate have arrived. The chooser opens the form while it is
     * unknown rather than turning the user away on a guess; the form keeps Create disabled until a
     * currency is seated.
     */
    val hasCommunityCurrency: StateFlow<Boolean?> =
        combine(tokenBalances, preferredRate) { balances, rate ->
            communityCurrencies(balances, rate).isNotEmpty()
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun choose(choice: Choice): Outcome = when (choice) {
        Choice.FindByUsername -> Outcome.Open(AppRoute.Messaging.FindByUsername)
        Choice.PrivateGroup -> Outcome.Open(AppRoute.Messaging.NewGroup(isPrivate = true))
        // Unknown counts as having one: the form keeps Create disabled until a currency is seated,
        // which is a better answer than a sheet that may be wrong.
        Choice.CommunityGroup ->
            if (hasCommunityCurrency.value == false) Outcome.NoCommunityCurrencies
            else Outcome.Open(AppRoute.Messaging.NewGroup())
    }
}
