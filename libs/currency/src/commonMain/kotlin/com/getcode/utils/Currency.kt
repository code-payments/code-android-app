package com.getcode.utils

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.util.resources.LocalResources
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType

val Currency.Companion.Kin: Currency
    get() = Currency(CurrencyCode.KIN.name, "Kin", CurrencyIcons.kin, "K", 1.00,)

@get:DrawableRes
val CurrencyCode.flagResId: Int?
    @Composable get() {
        if (this.name == "KIN") return CurrencyIcons.kin
        if (this.name == "XAF") return CurrencyIcons.xaf
        if (this.name == "XOF") return CurrencyIcons.xof

        return getRegion()?.name?.let {
            getFlag(LocalResources.current!!, it)
        }
    }

@DrawableRes
fun Currency.flagResId(resourceHelper: ResourceHelper): Int? {
    if (code == "KIN") return CurrencyIcons.kin
    if (code == "XAF") return CurrencyIcons.xaf
    if (code == "XOF") return CurrencyIcons.xof

    return CurrencyCode.tryValueOf(code)?.let { currency ->
        currency.getRegion()?.name
    }?.let { regionName ->
        getFlag(resourceHelper, regionName)
    }
}


@DrawableRes
@Suppress("DiscouragedApi")
private fun getFlag(resourceHelper: ResourceHelper, countryCode: String): Int? {
    if (countryCode.isEmpty()) return null
    val resourceName = "ic_flag_${countryCode.lowercase()}"
    return resourceHelper.getIdentifier(
        resourceName,
        ResourceType.Drawable,
    )
}
