package com.getcode.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.getcode.libs.currency.R
import com.getcode.model.Currency
import com.getcode.model.KinAmount
import com.getcode.util.resources.AndroidResources
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import com.getcode.utils.Kin
import com.getcode.utils.LocalCurrencyUtils
import com.getcode.utils.formatAmountString

fun KinAmount.formattedRaw() = FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())

@Composable
fun KinAmount.formatted(
    currency: Currency,
    amount: Double = fiat,
    suffix: String = stringResource(R.string.core_ofKin)
) = formatAmountString(
    resources = AndroidResources(context = LocalContext.current),
    currency = currency,
    amount = amount,
    suffix = suffix
)

@Composable
fun KinAmount.formatted(amount: Double = fiat, suffix: String = stringResource(R.string.core_ofKin)): String {
    val currency = LocalCurrencyUtils.current?.getCurrency(rate.currency.name)
        ?: Currency.Kin
    return formatted(currency = currency, amount = amount, suffix = suffix)
}

fun KinAmount.formatted(
    resources: ResourceHelper,
    currency: Currency,
    amount: Double = fiat,
    kinSuffix: String = "",
    suffix: String = resources.getString(R.string.core_ofKin)
) = formatAmountString(
    resources = resources,
    currency = currency,
    amount = amount,
    kinSuffix = kinSuffix,
    suffix = suffix
)