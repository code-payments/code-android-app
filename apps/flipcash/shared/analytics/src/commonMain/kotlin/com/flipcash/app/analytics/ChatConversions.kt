package com.flipcash.app.analytics

import com.flipcash.analytics.Amount
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.flipcash.analytics.ChatType as AnalyticsChatType

// The conversions the chat coordinator needs, kept in common code so it can run on desktop. The
// rest of the conversions stay in androidMain beside the types that need Android.

val ChatType.analytics: AnalyticsChatType
    get() = when (this) {
        ChatType.CONTACT_DM -> AnalyticsChatType.CONTACT
        ChatType.TIP_DM -> AnalyticsChatType.TIP
        ChatType.GROUP -> AnalyticsChatType.GROUP
        ChatType.UNKNOWN -> AnalyticsChatType.UNKNOWN
    }

/** No exchange rate or mint; `USDC` repeats the amount. */
val Fiat.analytics: Amount
    get() = Amount(
        fiat = decimalValue,
        currency = currencyCode.name,
        usdc = decimalValue,
        quarks = quarks,
    )

val Mint.analytics: String
    get() = base58()
