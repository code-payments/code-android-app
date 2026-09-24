package com.flipcash.app.analytics

import androidx.core.net.toUri
import com.flipcash.analytics.Amount
import com.flipcash.analytics.WalletProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.flipcash.analytics.ChatType as AnalyticsChatType
import com.flipcash.analytics.DisplayNameSource as AnalyticsDisplayNameSource
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.services.models.chat.ChatType

/*
 * Converts Android domain types into what the shared builders in `:libs:analytics-events`
 * take. This is the only place Android-specific formatting of an analytics value lives.
 */

val ChatType.analytics: AnalyticsChatType
    get() = when (this) {
        ChatType.CONTACT_DM -> AnalyticsChatType.CONTACT
        ChatType.TIP_DM -> AnalyticsChatType.TIP
        ChatType.GROUP -> AnalyticsChatType.GROUP
        ChatType.UNKNOWN -> AnalyticsChatType.UNKNOWN
    }

/** Android's `Error` format. iOS sends `domain.error:code`; part 2 settles one. */
val Throwable.analytics: String
    get() = message.orEmpty()

/** The native amount and currency, with the token amount as `USDC` and `Quarks`. */
val LocalFiat.analytics: Amount
    get() = Amount(
        fiat = nativeAmount.decimalValue,
        currency = rate.currency.name,
        usdc = underlyingTokenAmount.decimalValue,
        quarks = underlyingTokenAmount.quarks,
        exchangeRate = rate.fx,
        mint = mint.base58(),
    )

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

val OnRampProvider.UsesDeeplinks.analytics: WalletProvider
    get() = when (this) {
        OnRampProvider.Phantom -> WalletProvider.PHANTOM
    }

val DisplayNameSource.analytics: AnalyticsDisplayNameSource
    get() = when (this) {
        DisplayNameSource.Onboarding -> AnalyticsDisplayNameSource.ONBOARDING
        DisplayNameSource.MyAccount -> AnalyticsDisplayNameSource.MY_ACCOUNT
        DisplayNameSource.TipCardSetup -> AnalyticsDisplayNameSource.TIP_CARD_SETUP
    }

/**
 * The deeplink's `Type` property. Spelled out rather than taken from `javaClass.simpleName`,
 * which R8 renames in release builds.
 */
val DeeplinkType.analytics: String
    get() = when (this) {
        is DeeplinkType.Login -> "Login"
        is DeeplinkType.CashLink -> "CashLink"
        is DeeplinkType.TokenInfo -> "TokenInfo"
        is DeeplinkType.TipChat -> "TipChat"
        is DeeplinkType.GroupChatInvite -> "GroupChatInvite"
        is DeeplinkType.Tipcard -> "Tipcard"
        is DeeplinkType.TipcardByUsername -> "TipcardByUsername"
        is DeeplinkType.EmailVerification -> "EmailVerification"
    }

/** The URL with its query and fragment removed, which can carry secrets. */
fun String.withoutQueryOrFragment(): String {
    val uri = toUri()
    return try {
        uri.buildUpon()
            .clearQuery()
            .fragment(null)
            .build()
            .toString()
    } catch (_: Exception) {
        uri.path ?: this
    }
}
