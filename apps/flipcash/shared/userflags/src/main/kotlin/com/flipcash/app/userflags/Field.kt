package com.flipcash.app.userflags

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed class Field<Stored, Domain>(
    val preferenceKey: Preferences.Key<Stored>,
    val encode: (Domain) -> Stored,
    val decode: (Stored) -> Domain?,
) {
    data object PreferredProvider : Field<String, OnRampProvider>(
        stringPreferencesKey("override_preferred_provider"),
        encode = { it.encode() },
        decode = { decodeOnRampProvider(it) },
    )
    data object SupportedProviders : Field<Set<String>, List<OnRampProvider>>(
        stringSetPreferencesKey("override_supported_providers"),
        encode = { providers -> providers.map { it.encode() }.toSet() },
        decode = { strings -> strings.mapNotNull { decodeOnRampProvider(it) } },
    )
    data object MinimumVersion : Field<Int, Int>(
        intPreferencesKey("override_min_version"),
        encode = { it },
        decode = { it },
    )
    data object BillExchangeDataTimeout : Field<Long, Duration>(
        longPreferencesKey("override_bill_timeout"),
        encode = { it.inWholeMilliseconds },
        decode = { it.milliseconds },
    )
    data object NewCurrencyPurchaseAmount : Field<Long, Fiat>(
        longPreferencesKey("override_new_currency_amount"),
        encode = { it.quarks },
        decode = { Fiat(quarks = it) },
    )
}

internal fun OnRampProvider.encode(): String = when (this) {
    is OnRampProvider.Unknown -> "unknown"
    is OnRampProvider.ManualDeposit -> "manual_deposit"
    is OnRampProvider.Phantom -> "phantom"
    is OnRampProvider.Solflare -> "solflare"
    is OnRampProvider.Backpack -> "backpack"
    is OnRampProvider.Coinbase -> "coinbase"
}

// String → single provider
internal fun decodeOnRampProvider(raw: String): OnRampProvider? = when (raw) {
    "unknown" -> OnRampProvider.Unknown
    "manual_deposit" -> OnRampProvider.ManualDeposit
    "phantom" -> OnRampProvider.Phantom
    "solflare" -> OnRampProvider.Solflare
    "backpack" -> OnRampProvider.Backpack
    "coinbase" -> OnRampProvider.Coinbase(OnRampType.Virtual)
    else -> null
}