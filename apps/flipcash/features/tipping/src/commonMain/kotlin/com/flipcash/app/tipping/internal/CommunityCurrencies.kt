package com.flipcash.app.tipping.internal

import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint

/**
 * The community currencies the account holds, in the order the currency sheet lists them: USDF
 * left out, only balances that show a value in the preferred currency, highest first.
 *
 * One answer for two questions, so they cannot disagree. The New Chat chooser asks whether it is
 * empty (node 12370:77242's "No Community Currencies" sheet) and the form takes its first entry as
 * the default currency (node 12370:76881). Were the chooser to let a user through whom the form
 * then found nothing for, they would land on a form whose Create can never light up.
 *
 * The filter and [SelectTokenViewModel.BalanceOrder] are the sheet's own — the balances are
 * localized against [rate] the way [SelectTokenViewModel] does it for `TokenPurpose.Balance` — so
 * the default is always the sheet's top row.
 */
internal fun communityCurrencies(balances: List<TokenWithBalance>, rate: Rate): List<Mint> =
    balances
        .withoutUsdf { it.token.address }
        .map {
            TokenWithLocalizedBalance(
                token = it.token,
                balance = LocalFiat(usdf = it.balance, nativeAmount = it.balance.convertingTo(rate)),
            )
        }
        .filter { it.balance.nativeAmount.hasDisplayableValue }
        .sortedWith(SelectTokenViewModel.BalanceOrder)
        .map { it.token.address }

/**
 * The rows the currency sheet offers: everything but USDF. Dollars aren't a community currency, so
 * a group can't be gated on them. The sheet and [communityCurrencies] are the only things that set
 * the form's currency, so a draft can't hold USDF.
 */
internal fun <T> List<T>.withoutUsdf(mintOf: (T) -> Mint): List<T> =
    filterNot { mintOf(it) == Mint.usdf }
