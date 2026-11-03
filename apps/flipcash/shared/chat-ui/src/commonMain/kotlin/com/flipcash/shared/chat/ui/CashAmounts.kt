package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import com.flipcash.app.core.ui.forViewer
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint

/**
 * The USD a USDF payment settled at, which is what its token quarks are. Null for any other mint
 * (its quarks are not dollars) and for rows stored before the quarks were kept.
 */
fun MessageContent.Cash.settledUsd(): Fiat? {
    if (mint != Mint.usdf) return null
    return tokenQuarks?.takeIf { it > 0 }?.let { Fiat(it, CurrencyCode.USD) }
}

/**
 * This payment in the viewer's currency, from the USD it settled at when that is known and from
 * today's [rates] otherwise. The bubble reads the same through `inDisplayCurrency`.
 */
fun MessageContent.Cash.forViewer(preferredRate: Rate, rates: Map<CurrencyCode, Rate>): Fiat =
    settledUsd()
        ?.let { LocalFiat(usdf = it, nativeAmount = amount).forViewer(preferredRate, rates) }
        ?: amount.forViewer(preferredRate, rates)

/**
 * A payment in the viewer's currency, following it as it changes: from [settledUsd] when the
 * payment was USDF, so the bubble, a quote of it and the "You received" sheet all read one number.
 */
@Composable
fun cashInDisplayCurrency(amount: Fiat, settledUsd: Fiat?): Fiat =
    settledUsd
        ?.let { LocalFiat(usdf = it, nativeAmount = amount).inDisplayCurrency() }
        ?: amount.inDisplayCurrency()

/**
 * One recipient's [part] of a group send in the viewer's currency, read the way [cashInDisplayCurrency]
 * reads the whole: from the USD it settled at when the payment was USDF.
 */
@Composable
fun MessageContent.Cash.partInDisplayCurrency(part: MessageContent.Cash.Payment): Fiat =
    cashInDisplayCurrency(
        amount = Fiat(part.nativeAmount, amount.currencyCode),
        settledUsd = Fiat(part.tokenQuarks, CurrencyCode.USD).takeIf { mint == Mint.usdf && part.tokenQuarks > 0 },
    )

/**
 * [userId]'s part of this payment when the message names its recipients: their entry in
 * [MessageContent.Cash.multiReceiver], or the whole amount when they are the single [receiver].
 * Null when the message names other people only, or names no one.
 */
fun MessageContent.Cash.partFor(userId: ID?): MessageContent.Cash.Payment? = when {
    userId == null -> null
    multiReceiver.isNotEmpty() -> multiReceiver.firstOrNull { it.receiver == userId }
    receiver == userId -> MessageContent.Cash.Payment(userId, amount.decimalValue, tokenQuarks ?: 0L)
    else -> null
}
