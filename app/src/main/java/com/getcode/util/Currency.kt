package com.getcode.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.FormatUtils

val Currency.Companion.Kin: Currency
    get() = Currency(CurrencyCode.KIN.name, "Kin", R.drawable.ic_currency_kin, "K", 1.00)

@DrawableRes
fun Currency.flagResId(context: Context): Int? {
    if (code == "KIN") return R.drawable.ic_currency_kin
    if (code == "XAF") return R.drawable.ic_currency_xaf
    if (code == "XOF") return R.drawable.ic_currency_xof

    return CurrencyCode.tryValueOf(code)?.let { currency ->
        currency.getRegion()?.name
    }?.let { regionName ->
        getFlag(context, regionName)
    }
}


@get:DrawableRes
val CurrencyCode.flagResId: Int?
    @Composable get() {
        if (this.name == "KIN") return R.drawable.ic_currency_kin
        if (this.name == "XAF") return R.drawable.ic_currency_xaf
        if (this.name == "XOF") return R.drawable.ic_currency_xof

        return getRegion()?.name?.let {
            return getFlag(LocalContext.current, it)
        }
    }


@DrawableRes
@SuppressLint("DiscouragedApi")
fun getFlag(context: Context, countryCode: String): Int? {
    if (countryCode.isEmpty()) return null
    val resourceName = "ic_flag_${countryCode.lowercase()}"
    return context.resources.getIdentifier(
        resourceName,
        "drawable",
        BuildConfig.APPLICATION_ID
    ).let { if (it == 0) null else it }
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

fun formatAmountString(resources: ResourceHelper, currency: Currency, amount: Double): String {
    val isKin = currency.code == Currency.Kin.code

    return if (isKin) {
        "${FormatUtils.formatWholeRoundDown(amount)} ${
            resources.getString(R.string.core_kin)
        }"
    } else {
        when {
            currency.code == currency.symbol -> {
                "${FormatUtils.format(amount)} ${resources.getString(R.string.core_ofKin)}"
            }
            else -> {
                "${currency.symbol}${FormatUtils.format(amount)} ${resources.getString(R.string.core_ofKin)}"
            }
        }
    }
}