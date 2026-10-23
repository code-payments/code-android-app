package com.getcode.utils

import com.getcode.model.Currency
import com.getcode.util.resources.ResourceHelper

fun Currency.format(resources: ResourceHelper, amount: Double): String {
    return formatAmountString(
        resources,
        this,
        amount
    )
}

fun formatAmountString(
    resources: ResourceHelper,
    currency: Currency,
    amount: Double,
    kinSuffix: String = resources.getString(com.getcode.util.resources.R.string.core_kin),
    suffix: String = resources.getString(com.getcode.util.resources.R.string.core_ofKin)
): String {
    val isKin = currency.code == Currency.Kin.code

    return if (isKin) {
        "${FormatUtils.formatWholeRoundDown(amount)}${if (kinSuffix.isNotEmpty()) " $kinSuffix" else ""}"
    } else {
        when {
            currency.code == currency.symbol -> {
                FormatUtils.format(amount) + if (suffix.isNotEmpty()) " $suffix" else ""
            }
            else -> {
                "${currency.symbol}${FormatUtils.format(amount)}" + if (suffix.isNotEmpty()) " $suffix" else ""
            }
        }
    }
}