package com.flipcash.app.userflags

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.input.KeyboardType
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.shared.userflags.R
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed class Field<Stored, Domain>(
    val preferenceKey: Preferences.Key<Stored>,
    val encode: (Domain) -> Stored,
    val decode: (Stored) -> Domain?,
    @get:StringRes val label: Int,
    @get:StringRes val hint: Int = label,
    val format: (Domain) -> String,
    val editFormat: (Domain) -> String = format,
    val editor: FieldEditor<Domain>,
    val inputTransformation: InputTransformation? = null,
    val outputTransformation: OutputTransformation? = null,
) {
    data object PreferredProvider : Field<String, OnRampProvider.Defined?>(
        stringPreferencesKey("override_preferred_provider"),
        encode = { it?.encode() ?: "None" },
        decode = { decodeOnRampProvider(it) },
        label = R.string.label_flag_preferredProvider,
        format = { provider -> provider?.displayName() ?: "None" },
        editor = FieldEditor.SingleSelect(
            options = listOf(
                "Manual Deposit" to OnRampProvider.ManualDeposit,
                "Phantom" to OnRampProvider.Phantom,
                "Coinbase" to OnRampProvider.Coinbase(OnRampType.Virtual),
            )
        ),
    )

    data object SupportedProviders : Field<Set<String>, List<OnRampProvider.Defined>>(
        stringSetPreferencesKey("override_supported_providers"),
        encode = { providers -> providers.map { it.encode() }.toSet() },
        decode = { strings -> strings.mapNotNull { decodeOnRampProvider(it) } },
        format = { providers ->
            when (providers.size) {
                0 -> "None"
                1 -> providers.first().displayName()
                else -> "${providers.size} selected"
            }
        },
        label = R.string.label_flag_supportedOnRampProviders,
        editor = FieldEditor.MultiSelect(
            options = listOf(
                "Manual Deposit" to OnRampProvider.ManualDeposit,
                "Phantom" to OnRampProvider.Phantom,
                "Coinbase" to OnRampProvider.Coinbase(OnRampType.Virtual),
            )
        )
    )

    data object MinimumVersion : Field<Int, Int?>(
        intPreferencesKey("override_min_version"),
        encode = { it ?: 0 },
        decode = { it },
        label = R.string.label_flag_minimumVersion,
        hint = R.string.hint_flag_minimumVersion,
        format = { it.toString() },
        editor = FieldEditor.TextInput(
            keyboard = KeyboardType.Number,
            parse = { it.toIntOrNull() },
        ),
        inputTransformation = InputTransformation {
            if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
        },
    )

    data object BillExchangeDataTimeout : Field<Long, Duration?>(
        longPreferencesKey("override_bill_timeout"),
        encode = { it?.inWholeMilliseconds ?: -1L },
        decode = { if (it < 0) null else it.milliseconds },
        label = R.string.label_flag_billExchangeDataTimeout,
        hint = R.string.hint_flag_billExchangeDataTimeout,
        format = { it?.let { "${it.inWholeSeconds}s" } ?: "None" },
        editFormat = { it?.inWholeSeconds?.toString() ?: "" },
        editor = FieldEditor.TextInput(
            keyboard = KeyboardType.Number,
            parse = { it.toLongOrNull()?.let { secs -> (secs * 1000).milliseconds } },
        ),
        inputTransformation = InputTransformation {
            if (!asCharSequence().all { it.isDigit() }) revertAllChanges()
        },
    )

    data object NewCurrencyPurchaseAmount : Field<Long, Fiat>(
        longPreferencesKey("override_new_currency_amount"),
        encode = { it.quarks },
        decode = { Fiat(quarks = it) },
        label = R.string.label_flag_newCurrencyPurchaseAmount,
        hint = R.string.hint_flag_newCurrencyPurchaseAmount,
        format = { it.formatted(rule = Fiat.FormattingRule.Truncated) },
        editFormat = { it.formatted(showPrefix = false, rule = Fiat.FormattingRule.Truncated) },
        editor = FieldEditor.TextInput(
            keyboard = KeyboardType.Decimal,
            parse = { it.toDoubleOrNull()?.let { d -> Fiat(fiat = d) } },
        ),
        outputTransformation = OutputTransformation {
            if (length > 0) {
                insert(0, "$")
            }
        },
    )

    data object NewCurrencyFeeAmount : Field<Long, Fiat>(
        longPreferencesKey("override_new_fee_amount"),
        encode = { it.quarks },
        decode = { Fiat(quarks = it) },
        label = R.string.label_flag_newCurrencyFeeAmount,
        hint = R.string.hint_flag_newCurrencyFeeAmount,
        format = { it.formatted(rule = Fiat.FormattingRule.Truncated) },
        editFormat = { it.formatted(showPrefix = false, rule = Fiat.FormattingRule.Truncated) },
        editor = FieldEditor.TextInput(
            keyboard = KeyboardType.Decimal,
            parse = { it.toDoubleOrNull()?.let { d -> Fiat(fiat = d) } },
        ),
        outputTransformation = OutputTransformation {
            if (length > 0) {
                insert(0, "$")
            }
        },
    )

    data object WithdrawalFeeAmount : Field<Long, Fiat>(
        longPreferencesKey("override_withdrawal_fee_amount"),
        encode = { it.quarks },
        decode = { Fiat(quarks = it) },
        label = R.string.label_flag_withdrawalFeeAmount,
        hint = R.string.hint_flag_withdrawalFeeAmount,
        format = { it.formatted(rule = Fiat.FormattingRule.Truncated) },
        editFormat = { it.formatted(showPrefix = false, rule = Fiat.FormattingRule.Truncated) },
        editor = FieldEditor.TextInput(
            keyboard = KeyboardType.Decimal,
            parse = { it.toDoubleOrNull()?.let { d -> Fiat(fiat = d) } },
        ),
        outputTransformation = OutputTransformation {
            if (length > 0) {
                insert(0, "$")
            }
        },
    )

    data object PreferredUsdcOnRampLiquidityPool : Field<String, UsdcLiquidtyPool>(
        stringPreferencesKey("override_preferred_liquidity_pool"),
        encode = { it.name },
        decode = { runCatching { UsdcLiquidtyPool.valueOf(it) }.getOrNull() },
        label = R.string.label_flag_preferredUsdcLiquidityPool,
        format = { provider -> provider.displayName() },
        editor = FieldEditor.SingleSelect(
            options = listOf(
                "Flipcash" to UsdcLiquidtyPool.Flipcash,
                "Coinbase" to UsdcLiquidtyPool.CoinbaseStableSwapper,
            )
        ),
    )
}

internal fun OnRampProvider.Defined.encode(): String = when (this) {
    is OnRampProvider.ManualDeposit -> "manual_deposit"
    is OnRampProvider.Phantom -> "phantom"
    is OnRampProvider.Coinbase -> "coinbase"
}

// String → single provider
internal fun decodeOnRampProvider(raw: String): OnRampProvider.Defined? = when (raw) {
    "manual_deposit" -> OnRampProvider.ManualDeposit
    "phantom" -> OnRampProvider.Phantom
    "coinbase" -> OnRampProvider.Coinbase(OnRampType.Virtual)
    else -> null
}

private fun OnRampProvider.Defined.displayName(): String = when (this) {
    is OnRampProvider.ManualDeposit -> "Manual Deposit"
    is OnRampProvider.Phantom -> "Phantom"
    is OnRampProvider.Coinbase -> "Coinbase"
}

private fun UsdcLiquidtyPool.displayName(): String = when (this) {
    UsdcLiquidtyPool.Unknown -> "Unknown"
    UsdcLiquidtyPool.Flipcash -> "Flipcash"
    UsdcLiquidtyPool.CoinbaseStableSwapper -> "Coinbase Stable Swapper"
}