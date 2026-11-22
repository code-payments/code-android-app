package com.flipcash.app.messenger.internal.request

import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance

/**
 * The rows of a collection's "Select Payment Currency" list (frame 6.3, node 13996:243): the rows
 * the currency sheet lists for `TokenPurpose.Select`, balances that show a value in [rate]'s
 * currency in [SelectTokenViewModel.BalanceOrder], plus [paying] at any balance. A collection pays
 * in its own currency even when it holds none, so the list has to show it (group-collections
 * spec, "Default currency").
 */
internal fun collectionCurrencies(
    balances: List<TokenWithBalance>,
    paying: Token?,
    rate: Rate,
): List<TokenWithLocalizedBalance> {
    val held = balances
        .map {
            TokenWithLocalizedBalance(
                token = it.token,
                balance = LocalFiat(usdf = it.balance, nativeAmount = it.balance.convertingTo(rate)),
            )
        }
        .filter { it.balance.nativeAmount.hasDisplayableValue }
    val payingRow = paying
        ?.takeIf { held.none { row -> row.token.address == it.address } }
        ?.let {
            TokenWithLocalizedBalance(
                token = it,
                balance = LocalFiat(
                    usdf = Fiat.Zero,
                    nativeAmount = Fiat(quarks = 0, currencyCode = rate.currency),
                ),
            )
        }
    return (held + listOfNotNull(payingRow)).sortedWith(SelectTokenViewModel.BalanceOrder)
}
