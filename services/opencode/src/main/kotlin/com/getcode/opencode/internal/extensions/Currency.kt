package com.getcode.opencode.internal.extensions

import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType

fun Currency.Companion.fromCode(code: CurrencyCode, resources: ResourceHelper): Currency {
    val resId = resources.getFlagByCurrency(code.getRegion()?.name.orEmpty())
    val currencyJava = java.util.Currency.getInstance(code.name)

    return Currency(
        code = currencyJava.currencyCode,
        name = currencyJava.displayName,
        resId = resId,
        symbol = code.singleCharacterCurrencySymbol.orEmpty()
    )
}

private fun ResourceHelper.getFlagByCurrency(countryCode: String): Int? {
    if (countryCode.isEmpty()) return null
    val resourceName = "ic_flag_${countryCode.lowercase()}"
    return getIdentifier(
        resourceName,
        ResourceType.Drawable
    ).let { if (it == 0) null else it }
}