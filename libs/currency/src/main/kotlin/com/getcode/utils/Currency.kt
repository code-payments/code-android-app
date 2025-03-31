package com.getcode.utils

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import com.getcode.libs.currency.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.util.resources.LocalResources
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType

val Currency.Companion.Kin: Currency
    get() = Currency(CurrencyCode.KIN.name, "Kin", R.drawable.ic_currency_kin, "K", 1.00)

@get:DrawableRes
val CurrencyCode.flagResId: Int?
    @Composable get() {
        if (this.name == "KIN") return R.drawable.ic_currency_kin
        if (this.name == "XAF") return R.drawable.ic_currency_xaf
        if (this.name == "XOF") return R.drawable.ic_currency_xof

        return getRegion()?.name?.let {
            getFlag(LocalResources.current!!, it)
        }
    }

@DrawableRes
fun Currency.flagResId(resourceHelper: ResourceHelper): Int? {
    if (code == "KIN") return R.drawable.ic_currency_kin
    if (code == "XAF") return R.drawable.ic_currency_xaf
    if (code == "XOF") return R.drawable.ic_currency_xof

    return CurrencyCode.tryValueOf(code)?.let { currency ->
        currency.getRegion()?.name
    }?.let { regionName ->
        getFlag(resourceHelper, regionName)
    }
}


@DrawableRes
@SuppressLint("DiscouragedApi")
private fun getFlag(resourceHelper: ResourceHelper, countryCode: String): Int? {
    if (countryCode.isEmpty()) return null
    val resourceName = "ic_flag_${countryCode.lowercase()}"
    return resourceHelper.getIdentifier(
        resourceName,
        ResourceType.Drawable,
    )
}

fun Currency.format(resources: ResourceHelper, amount: KinAmount): String {
    return formatAmountString(
        resources,
        this,
        amount.fiat
    )
}

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
    kinSuffix: String = resources.getKinSuffix(),
    suffix: String = resources.getOfKinSuffix()
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