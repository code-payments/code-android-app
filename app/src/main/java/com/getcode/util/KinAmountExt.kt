package com.getcode.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.getcode.LocalCurrencyUtils
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.KinAmount
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils

fun KinAmount.formattedRaw() = FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())

@Composable
fun KinAmount.formatted(
    currency: Currency,
    suffix: String = stringResource(R.string.core_ofKin)
) = formatAmountString(
    resources = AndroidResources(context = LocalContext.current),
    currency = currency,
    amount = fiat,
    suffix = suffix
)

@Composable
fun KinAmount.formatted(suffix: String = stringResource(R.string.core_ofKin)): String {
    val currency = LocalCurrencyUtils.current?.getCurrency(rate.currency.name)
        ?: Currency.Kin
    return formatted(currency = currency, suffix = suffix)
}

fun KinAmount.formatted(
    resources: ResourceHelper,
    currency: Currency,
    kinSuffix: String = "",
    suffix: String = resources.getString(R.string.core_ofKin)
) = formatAmountString(
    resources = resources,
    currency = currency,
    amount = fiat,
    kinSuffix = kinSuffix,
    suffix = suffix
)