package com.getcode.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.getcode.LocalCurrencyUtils
import com.getcode.model.Currency
import com.getcode.model.KinAmount
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import timber.log.Timber

fun KinAmount.formattedRaw() = FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())

@Composable
fun KinAmount.formatted(currency: Currency) = formatAmountString(
    AndroidResources(LocalContext.current),
    currency,
    fiat
)

@Composable
fun KinAmount.formatted(): String {
    val currency = LocalCurrencyUtils.current?.getCurrency(rate.currency.name)
        ?: Currency.Kin
    Timber.d("formatted currency=${currency.code}")
    return formatted(currency = currency)
}

fun KinAmount.formatted(resources: ResourceHelper, currency: Currency) = formatAmountString(
    resources,
    currency,
    fiat
)